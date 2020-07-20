package com.google.sps.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.sps.workspace.Workspace;
import com.google.sps.workspace.WorkspaceFactory;
import java.time.Clock;
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
public class EnterQueueTest {

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(
          // Use High Rep job policy to allow cross group transactions in tests.
          new LocalDatastoreServiceTestConfig().setApplyAllHighRepJobPolicy());

  private DatastoreService datastore;

  @Mock HttpServletRequest httpRequest;

  @Mock HttpServletResponse httpResponse;

  @Mock FirebaseAuth authInstance;

  @Mock Clock clock;

  @Mock WorkspaceFactory workspaceFactory;
  @Mock Workspace workspace;

  @InjectMocks EnterQueue addFirst;

  private Clock fixedClock;
  private static final LocalDate LOCAL_DATE = LocalDate.of(2020, 07, 06);
  private static final Date DATE =
      Date.from(LOCAL_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant());

  private Filter classVisitFilter;
  private Filter dateVisitFilter;
  private CompositeFilter visitFilter;
  private Query visitQuery;
  private Entity init;

  @Before
  public void setUp() {
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();
    System.setProperty(
        DatastoreServiceConfig.DATASTORE_EMPTY_LIST_SUPPORT, Boolean.TRUE.toString());

    fixedClock =
        Clock.fixed(
            LOCAL_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    doReturn(fixedClock.instant()).when(clock).instant();
    doReturn(fixedClock.getZone()).when(clock).getZone();

    init = new Entity("Class");

    classVisitFilter = new FilterPredicate("classKey", FilterOperator.EQUAL, init.getKey());
    dateVisitFilter = new FilterPredicate("date", FilterOperator.EQUAL, DATE);
    visitFilter = CompositeFilterOperator.and(dateVisitFilter, classVisitFilter);

    visitQuery = new Query("Visit").setFilter(visitFilter);
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
  public void addFirstStudentToQueue() throws Exception {
    init.setProperty("owner", "ownerID");
    init.setProperty("name", "testClass");
    init.setProperty("beingHelped", new EmbeddedEntity());
    init.setProperty("studentQueue", Collections.emptyList());

    datastore.put(init);

    Entity initUser = new Entity("User");

    initUser.setProperty("userEmail", "user@google.com");
    initUser.setProperty("registeredClasses", Arrays.asList(init.getKey()));
    initUser.setProperty("ownedClasses", Collections.emptyList());
    initUser.setProperty("taClasses", Collections.emptyList());

    datastore.put(initUser);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("token");

    FirebaseToken mockToken = mock(FirebaseToken.class);
    when(authInstance.verifyIdToken("token")).thenReturn(mockToken);
    when(mockToken.getUid()).thenReturn("studentID");

    UserRecord mockUser = mock(UserRecord.class);
    when(authInstance.getUser("studentID")).thenReturn(mockUser);
    when(mockUser.getEmail()).thenReturn("user@google.com");

    when(workspaceFactory.create(anyString())).thenReturn(workspace);

    addFirst.doPost(httpRequest, httpResponse);

    Entity testClassEntity = datastore.prepare(new Query("Class")).asSingleEntity();
    ArrayList<EmbeddedEntity> testQueue =
        (ArrayList<EmbeddedEntity>) testClassEntity.getProperty("studentQueue");
    assertEquals(1, testQueue.size());
    assertTrue(testQueue.get(0).hasProperty("studentID"));

    Entity testVisitEntity = datastore.prepare(visitQuery).asSingleEntity();
    assertEquals(1, (long) testVisitEntity.getProperty("numVisits"));
    assertEquals(DATE, (Date) testVisitEntity.getProperty("date"));

    verify(workspaceFactory, times(1)).create(eq(KeyFactory.keyToString(init.getKey())));
    verify(workspace, times(1)).setStudentUID(eq("studentID"));
  }

  @Test
  public void addUniqueStudentToQueue() throws Exception {
    init.setProperty("owner", "ownerID");
    init.setProperty("name", "testClass");
    init.setProperty("beingHelped", Collections.emptyList());

    EmbeddedEntity addQueue = new EmbeddedEntity();
    EmbeddedEntity studentInfo = new EmbeddedEntity();
    studentInfo.setProperty("timeEntered", DATE);
    addQueue.setProperty("test1", studentInfo);
    init.setProperty("studentQueue", Arrays.asList(addQueue));

    datastore.put(init);

    Entity visitInit = new Entity("Visit");

    visitInit.setProperty("classKey", init.getKey());
    visitInit.setProperty("date", DATE);
    visitInit.setProperty("numVisits", 1);

    datastore.put(visitInit);

    Entity initUser = new Entity("User");

    initUser.setProperty("userEmail", "user@google.com");
    initUser.setProperty("registeredClasses", Arrays.asList(init.getKey()));
    initUser.setProperty("ownedClasses", Collections.emptyList());
    initUser.setProperty("taClasses", Collections.emptyList());

    datastore.put(initUser);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("testID");

    FirebaseToken mockToken = mock(FirebaseToken.class);
    when(authInstance.verifyIdToken("testID")).thenReturn(mockToken);
    when(mockToken.getUid()).thenReturn("uID");

    UserRecord mockUser = mock(UserRecord.class);
    when(authInstance.getUser("uID")).thenReturn(mockUser);
    when(mockUser.getEmail()).thenReturn("user@google.com");

    when(workspaceFactory.create(anyString())).thenReturn(workspace);

    addFirst.doPost(httpRequest, httpResponse);

    Entity testClassEntity = datastore.prepare(new Query("Class")).asSingleEntity();

    ArrayList<EmbeddedEntity> testQueue =
        (ArrayList<EmbeddedEntity>) testClassEntity.getProperty("studentQueue");
    assertEquals(
        KeyFactory.keyToString(init.getKey()), KeyFactory.keyToString(testClassEntity.getKey()));
    assertEquals(2, testQueue.size());
    assertTrue(testQueue.get(0).hasProperty("test1"));
    assertTrue(testQueue.get(1).hasProperty("uID"));

    Entity testVisitEntity = datastore.prepare(visitQuery).asSingleEntity();
    assertEquals(2, (long) testVisitEntity.getProperty("numVisits"));
    assertEquals(DATE, (Date) testVisitEntity.getProperty("date"));

    verify(workspaceFactory, times(1)).create(eq(KeyFactory.keyToString(init.getKey())));
    verify(workspace, times(1)).setStudentUID(eq("uID"));
  }

  @Test
  public void addDuplicateStudentToQueue() throws Exception {
    init.setProperty("owner", "ownerID");
    init.setProperty("name", "testClass");
    init.setProperty("beingHelped", Collections.emptyList());

    EmbeddedEntity addQueue = new EmbeddedEntity();
    EmbeddedEntity studentInfo = new EmbeddedEntity();
    studentInfo.setProperty("timeEntered", DATE);
    addQueue.setProperty("uID", studentInfo);
    init.setProperty("studentQueue", Arrays.asList(addQueue));

    datastore.put(init);

    Entity visitInit = new Entity("Visit");

    visitInit.setProperty("classKey", init.getKey());
    visitInit.setProperty("date", DATE);
    visitInit.setProperty("numVisits", 1);

    datastore.put(visitInit);

    Entity initUser = new Entity("User");

    initUser.setProperty("userEmail", "user@google.com");
    initUser.setProperty("registeredClasses", Arrays.asList(init.getKey()));
    initUser.setProperty("ownedClasses", Collections.emptyList());
    initUser.setProperty("taClasses", Collections.emptyList());

    datastore.put(initUser);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("testID");

    FirebaseToken mockToken = mock(FirebaseToken.class);
    when(authInstance.verifyIdToken("testID")).thenReturn(mockToken);
    when(mockToken.getUid()).thenReturn("uID");

    UserRecord mockUser = mock(UserRecord.class);
    when(authInstance.getUser("uID")).thenReturn(mockUser);
    when(mockUser.getEmail()).thenReturn("user@google.com");

    addFirst.doPost(httpRequest, httpResponse);

    Entity testClassEntity = datastore.prepare(new Query("Class")).asSingleEntity();

    ArrayList<EmbeddedEntity> testQueue =
        (ArrayList<EmbeddedEntity>) testClassEntity.getProperty("studentQueue");
    assertEquals(
        KeyFactory.keyToString(init.getKey()), KeyFactory.keyToString(testClassEntity.getKey()));
    assertEquals(1, testQueue.size());
    assertTrue(testQueue.get(0).hasProperty("uID"));

    Entity testVisitEntity = datastore.prepare(visitQuery).asSingleEntity();
    assertEquals(1, (long) testVisitEntity.getProperty("numVisits"));
    assertEquals(DATE, (Date) testVisitEntity.getProperty("date"));
  }

  @Test
  public void redirectVerifiedTA() throws Exception {
    Entity init = new Entity("Class");

    init.setProperty("owner", "ownerID");
    init.setProperty("name", "testClass");
    init.setProperty("beingHelped", Collections.emptyList());
    init.setProperty("studentQueue", Collections.emptyList());

    datastore.put(init);

    Entity initUser = new Entity("User");

    initUser.setProperty("userEmail", "taEmail");
    initUser.setProperty("registeredClasses", Collections.emptyList());
    initUser.setProperty("ownedClasses", Collections.emptyList());
    initUser.setProperty("taClasses", Arrays.asList(init.getKey()));

    datastore.put(initUser);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("testID");

    FirebaseToken mockToken = mock(FirebaseToken.class);
    when(authInstance.verifyIdToken("testID")).thenReturn(mockToken);
    when(mockToken.getUid()).thenReturn("uID");

    UserRecord mockTA = mock(UserRecord.class);
    when(authInstance.getUser("uID")).thenReturn(mockTA);
    when(mockTA.getEmail()).thenReturn("taEmail");

    addFirst.doPost(httpRequest, httpResponse);

    verify(httpResponse)
        .addHeader("Location", "/queue/ta.html?classCode=" + KeyFactory.keyToString(init.getKey()));
  }

  @Test
  public void redirectUnverifiedTA() throws Exception {
    Entity init = new Entity("Class");

    init.setProperty("owner", "ownerID");
    init.setProperty("name", "testClass");
    init.setProperty("beingHelped", Collections.emptyList());
    init.setProperty("studentQueue", Collections.emptyList());

    datastore.put(init);

    Entity initUser = new Entity("User");

    initUser.setProperty("userEmail", "dne");
    initUser.setProperty("registeredClasses", Collections.emptyList());
    initUser.setProperty("ownedClasses", Collections.emptyList());
    initUser.setProperty("taClasses", Collections.emptyList());

    datastore.put(initUser);

    when(httpRequest.getParameter("classCode")).thenReturn(KeyFactory.keyToString(init.getKey()));
    when(httpRequest.getParameter("idToken")).thenReturn("testID");

    FirebaseToken mockToken = mock(FirebaseToken.class);
    when(authInstance.verifyIdToken("testID")).thenReturn(mockToken);
    when(mockToken.getUid()).thenReturn("uID");

    UserRecord mockTA = mock(UserRecord.class);
    when(authInstance.getUser("uID")).thenReturn(mockTA);
    when(mockTA.getEmail()).thenReturn("dne");

    addFirst.doPost(httpRequest, httpResponse);

    verify(httpResponse).sendError(403);
  }
}
