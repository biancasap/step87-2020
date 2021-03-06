package com.google.sps.servlets.queue;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.sps.authentication.Authenticator;
import com.google.sps.tasks.TaskScheduler;
import com.google.sps.tasks.TaskSchedulerFactory;
import com.google.sps.workspace.Workspace;
import com.google.sps.workspace.WorkspaceFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoveFromQueueTest {

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          // Use High Rep job policy to allow cross group transactions in tests.
          new LocalDatastoreServiceTestConfig().setApplyAllHighRepJobPolicy());

  private DatastoreService datastore;
  private String QUEUE_NAME = "QUEUE_NAME";

  @Mock HttpServletRequest httpRequest;
  @Mock HttpServletResponse httpResponse;
  @Mock FirebaseAuth authInstance;
  @Mock WorkspaceFactory factory;
  @Mock Workspace workspace;
  @Mock Authenticator auth;
  @Mock TaskScheduler scheduler;
  @Mock TaskSchedulerFactory taskSchedulerFactory;

  @InjectMocks RemoveFromQueue removeFromQueue;

  private static final Date START_DATE =
      Date.from(LocalDate.of(2020, 07, 06).atStartOfDay(ZoneId.systemDefault()).toInstant());

  @Before
  public void setUp() {
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();
  }

  @After
  public void tearDown() {
    // Clean up any dangling transactions.
    Transaction txn = datastore.getCurrentTransaction(null);
    if (txn != null && txn.isActive()) {
      txn.rollback();
    }

    helper.tearDown();
  }

  @Test
  public void takeSelfOff() throws Exception {
    String WORKSPACE_ID1 = "WORKSPACE_ID";
    String WORKSPACE_ID2 = "WORKSPACE_ID2";

    Entity init = new Entity("Class");

    init.setProperty("name", "testClass");

    EmbeddedEntity addQueue1 = new EmbeddedEntity();
    addQueue1.setProperty("timeEntered", START_DATE);
    addQueue1.setProperty("workspaceID", WORKSPACE_ID1);
    addQueue1.setProperty("uID", "studentID");

    EmbeddedEntity addQueue2 = new EmbeddedEntity();
    addQueue2.setProperty("timeEntered", START_DATE);
    addQueue2.setProperty("workspaceID", WORKSPACE_ID2);
    addQueue2.setProperty("uID", "ID2");

    init.setProperty("studentQueue", Arrays.asList(addQueue1, addQueue2));

    EmbeddedEntity beingHelped = new EmbeddedEntity();
    init.setProperty("beingHelped", beingHelped);

    datastore.put(init);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("token");
    when(auth.verifyInClass("token", KeyFactory.keyToString(init.getKey()))).thenReturn(true);

    FirebaseToken mockToken = mock(FirebaseToken.class);
    when(authInstance.verifyIdToken("token")).thenReturn(mockToken);
    when(mockToken.getUid()).thenReturn("studentID");

    when(factory.fromWorkspaceID(WORKSPACE_ID1)).thenReturn(workspace);

    removeFromQueue.doPost(httpRequest, httpResponse);

    Entity testClassEntity = datastore.prepare(new Query("Class")).asSingleEntity();

    ArrayList<EmbeddedEntity> testQueue =
        (ArrayList<EmbeddedEntity>) testClassEntity.getProperty("studentQueue");
    assertEquals(
        KeyFactory.keyToString(init.getKey()), KeyFactory.keyToString(testClassEntity.getKey()));
    assertEquals(1, testQueue.size());
  }

  @Test
  public void notStudent() throws Exception {
    Entity init = new Entity("Class");
    init.setProperty("name", "testClass");
    init.setProperty("beingHelped", new EmbeddedEntity());
    init.setProperty("studentQueue", Collections.emptyList());

    datastore.put(init);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("token");
    when(auth.verifyInClass("token", KeyFactory.keyToString(init.getKey()))).thenReturn(false);

    removeFromQueue.doPost(httpRequest, httpResponse);

    verify(httpResponse, times(1)).sendError(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  public void leaveQueueBeingHelped() throws Exception {
    Entity init = new Entity("Class");
    init.setProperty("name", "testClass");

    EmbeddedEntity queueInfo = new EmbeddedEntity();
    queueInfo.setProperty("taID", "taID");
    queueInfo.setProperty("workspaceID", "workspaceID");

    EmbeddedEntity beingHelped = new EmbeddedEntity();
    beingHelped.setProperty("uID", queueInfo);

    init.setProperty("beingHelped", beingHelped);

    init.setProperty("studentQueue", Collections.emptyList());

    datastore.put(init);

    Entity initUser = new Entity("User");

    initUser.setProperty("userEmail", "user@google.com");
    initUser.setProperty("registeredClasses", Arrays.asList(init.getKey()));
    initUser.setProperty("ownedClasses", Collections.emptyList());
    initUser.setProperty("taClasses", Collections.emptyList());

    datastore.put(initUser);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("testID");
    when(auth.verifyInClass("testID", KeyFactory.keyToString(init.getKey()))).thenReturn(true);

    FirebaseToken mockToken = mock(FirebaseToken.class);
    when(authInstance.verifyIdToken("testID")).thenReturn(mockToken);
    when(mockToken.getUid()).thenReturn("uID");

    when(taskSchedulerFactory.create(anyString(), anyString())).thenReturn(scheduler);
    removeFromQueue.QUEUE_NAME = QUEUE_NAME;
    removeFromQueue.doPost(httpRequest, httpResponse);
    Entity testClassEntity = datastore.prepare(new Query("Class")).asSingleEntity();

    EmbeddedEntity got = (EmbeddedEntity) testClassEntity.getProperty("beingHelped");
    assertThat((EmbeddedEntity) got.getProperty("uID")).isNull();
  }
}
