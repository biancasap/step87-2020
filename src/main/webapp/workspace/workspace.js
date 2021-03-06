let tabs = {};
let currentTab;
let jitsiVisible = false;

/* jshint ignore:start */
function defineTheme() {
  monaco.editor.defineTheme('dark-mode', {
    base: 'vs-dark', // can also be vs-dark or hc-black
    inherit: true, // can also be false to completely replace the builtin rules
    rules: [
      { token: '', foreground: 'D4D4D4', background: '000000' }
    ],
    colors: {
      "editor.background": '#000000',
      "editor.foreground": '#D4D4D4' 
    }
  });
}

/**
 * Gets the base firebase reference for this workspace.
 */
function getFirebaseRef() {
  const workspaceID = getParam("workspaceID");
  if (workspaceID !== null) {
    return firebase.database().ref().child(workspaceID);
  } else {
    // If we were not given a workspace ID redirect to the home page.
    window.location.href = "/";
  }
}

/**
 * Scroll event handler that allows users to scroll tabs using
 * a scroll wheel. 
 */
function scrollTabs(event) {
  // Only translate vertical scrolling to horizontal scrolling.
  if (event.deltaY) {
    this.scrollLeft += (event.deltaY);
    event.preventDefault();
  }
}

/**
 * Switches the current tab.
 * @param {String} tab name of the tab to switch to . 
 */
function switchTab(tab) {
  if (currentTab !== tab) {
    if (tabs[currentTab]) {
      tabs[currentTab].editorContainer.classList.add("hidden");
      tabs[currentTab].tabButton.classList.remove("active-tab");
      tabs[currentTab].tabButton.classList.add("inactive-tab");
    }

    tabs[tab].editorContainer.classList.remove("hidden");
    tabs[tab].tabButton.classList.remove("inactive-tab");
    tabs[tab].tabButton.classList.add("active-tab");

    tabs[tab].editor.layout();

    currentTab = tab;
  }
}

/**
 * Firebase does not accept the folowing characters: 
 * .$[]#/
 * We must encode them to store them in realtime database.
 * @param {String} filename  filename to encode.
 */
function encodeFileName(filename) {
  return encodeURIComponent(filename).replace(/\./g, '%2E');
}

/**
 * Decodes a file name from the realtime database
 * @param {String} filename  filename to decode.
 */
function decodeFileName(filename) {
  return decodeURIComponent(filename.replace(/%2E/g, "."));
}

function createTabButton(filename) {
  const tab = document.createElement("button");
  tab.classList.add("inactive-tab", "tab");
  tab.innerText = filename;
  tab.draggable = true;

  tab.addEventListener("dragstart", (event) => {
    switchTab(filename);

    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData('text/plain', filename);
  });

  tab.addEventListener("dragover", (event) => {
    if (event.preventDefault) {
      event.preventDefault();
    }

    event.dataTransfer.dropEffect = "move";

    return false;
  });

  tab.addEventListener("dragenter", (event) => {
    tab.classList.add("dragOver");
  });

  tab.addEventListener("dragleave", (event) => {
    tab.classList.remove("dragOver");
  });

  tab.addEventListener("dragend", (event) => {
    const dragOverEles = document.getElementsByClassName("dragOver");
    for (var i = 0; i < dragOverEles.length; i++) {
      dragOverEles[i].classList.remove("dragOver");
    }
  });

  tab.addEventListener("drop", (event) => {
    if(event.stopPropagation) {
      event.stopPropagation();
    }

    const otherFilename = event.dataTransfer.getData('text/plain');

    if (otherFilename !== filename) {
      const otherTab = tabs[otherFilename].tabButton;
      const tabsContiner = document.getElementById("tabs-container");
      const thisIndex = Array.from(tabsContiner.children).indexOf(tab);
      const otherIndex = Array.from(tabsContiner.children).indexOf(otherTab);

      if(otherIndex < thisIndex) {
        // Insert after this tab
        otherTab.remove();
        tabsContiner.insertBefore(otherTab, tab.nextSibling);
      } else {
        // Insert before this tab
        otherTab.remove();
        tabsContiner.insertBefore(otherTab, tab);
      }
    }

    return false;
  });

  tab.onclick = (event) => {
    switchTab(filename);
  };

  return tab;
}

/**
 * Creates a new tab with the given filename and the given contents.
 * @param {String} filename The filename for the tab.
 * @param {String} contents  The contents of the file. If non null the 
 * tab will be initialized with this string.
 */
function createNewTab(filename, contents) {
  if (!tabs[filename]) {
    // Add filename to tabs immediately so that the tab is not added
    // twice in the firebase child_added callback.
    tabs[filename] = {};

    const tab = createTabButton(filename);

    const firepadContainer = document.createElement("div");
    firepadContainer.classList.add("hidden", "firepad-container");

    const fileExtension = "." + filename.split('.').pop();

    const languages = monaco.languages.getLanguages().filter((lang) => lang.extensions.includes(fileExtension));

    const language = languages.length >= 1 ? languages[0].id : "plaintext";

    const editor = monaco.editor.create(firepadContainer, {
      language: language,
      theme: "dark-mode"
    });

    //Use LF
    editor.getModel().setEOL("\n");
    
    const firepad = Firepad.fromMonaco(getFirebaseRef().child("files").child(encodeFileName(filename)), editor);
    firepad.setUserColor("#6c2336");

    if (contents !== null) {
      firepad.on("ready", () => {
        firepad.setText(contents);
      });
    }

    tabs[filename] = {
      editor: editor,
      firepad: firepad,
      tabButton: tab,
      editorContainer: firepadContainer
    };

    document.getElementById("tabs-container").appendChild(tab);
    document.getElementById("firepads").appendChild(firepadContainer);
  } else if (contents !== null) {
    tabs[filename].firepad.setText(contents);
  }

}

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("tabs-container").onwheel = scrollTabs;

  require.config({ paths: {'vs': 'https://unpkg.com/monaco-editor@latest/min/vs'}});
  require(['vs/editor/editor.main'], function() {
    // Once the monaco library is loaded, we can start uploading files.
    const hiddenElements = document.getElementsByClassName("initially-hidden");

    for (let e of hiddenElements) {
      e.classList.remove("initially-hidden");
    }

    defineTheme();

    getFirebaseRef().child("files").on("child_added", (snapshot) => {
      const filename = decodeFileName(snapshot.key);
      createNewTab(filename, null);
      if (!currentTab) {
        switchTab(filename);
      }
    });
  });
});

window.onresize = () => {
  if (currentTab) {
    tabs[currentTab].editor.layout();
  }
};

/**
 * Called when the upload files button is clicked.
 */
function uploadFiles() {
  document.getElementById("upload-files").click();
}

/**
 * Called when files have been uploaded.
 */
async function filesUploaded() { // jshint ignore:line
  const files = document.getElementById("upload-files").files;

  for(var file of files) {
    // Convert to LF if in CRLF or CR
    let contents =  (await file.text()).replace(/\r\n?/g, "\n"); // jshint ignore:line
    createNewTab(file.name, contents);
  }

  switchTab(files[0].name);
}

function downloadFiles() {
  const downloadButton = document.getElementById("downloadButton");
  downloadButton.classList.add("download-in-progress");
  downloadButton.disabled = true;
  getToken().then(tok => {
    fetch(`/workspace/queueDownload?workspaceID=${getParam("workspaceID")}&idToken=${tok}`)
    .then(resp => resp.text()).then(downloadID => {
      getFirebaseRef().child("downloads").child(downloadID).on("value", snap => {
        if (snap.val() !== null) {
          const downloadLink = document.getElementById("downloadLink");
          downloadLink.href = `/workspace/downloadWorkspace?filename=${snap.val()}`;
          downloadLink.click();
          getFirebaseRef().child("downloads").child(downloadID).off("value");
          downloadButton.classList.remove("download-in-progress");
          downloadButton.disabled = false;
        }
      });
    });
  });
}