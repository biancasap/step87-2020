package com.google.sps.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
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
public class VisitTest {

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  private DatastoreService datastore;

  @Mock HttpServletRequest httpRequest;

  @Mock HttpServletResponse httpResponse;

  @InjectMocks Visits checkVisits;

  @Before
  public void setUp() {
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  // 1 Class
  public void checkVisits() throws Exception {
    ArrayList<String> listOfClassNames = new ArrayList<String>();
    ArrayList<Long> visitsPerClass = new ArrayList<Long>();

    Entity visitEntity = new Entity("Visit");
    visitEntity.setProperty("classKey", "testClass101");
    visitEntity.setProperty("numVisits", 15);
    visitEntity.setProperty("className", "exampleClassName");

    datastore.put(visitEntity);

    // Obtain visits from datastore and filter them into results query
    Query query = new Query("Visit");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    // Store the class name and number of visits into two separate lists
    for (Entity entity : results.asIterable()) {
      String className = (String) entity.getProperty("className");
      long classVisits = (long) entity.getProperty("numVisits");

      listOfClassNames.add(className);
      visitsPerClass.add(classVisits);
    }

    assertEquals((String) "exampleClassName", (String) listOfClassNames.get(0));
    assertEquals((long) 15, (long) visitsPerClass.get(0));
  }

  @Test
  // 1 Class w/ JSON Output
  public void checkVisitJSONOutput() throws Exception {
    ArrayList<String> listOfClassNames = new ArrayList<String>();
    ArrayList<Long> visitsPerClass = new ArrayList<Long>();

    Entity visitEntity = new Entity("Visit");
    visitEntity.setProperty("classKey", "testClass101");
    visitEntity.setProperty("numVisits", 20);
    visitEntity.setProperty("className", "exampleClassName");

    datastore.put(visitEntity);

    // Obtain visits from datastore and filter them into results query
    Query query = new Query("Visit");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    // Store the class name and number of visits into two separate lists
    for (Entity entity : results.asIterable()) {
      String className = (String) entity.getProperty("className");
      long classVisits = (long) entity.getProperty("numVisits");

      listOfClassNames.add(className);
      visitsPerClass.add(classVisits);
    }

    assertEquals((String) "exampleClassName", (String) listOfClassNames.get(0));
    assertEquals((long) 20, (long) visitsPerClass.get(0));

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(httpResponse.getWriter()).thenReturn(writer);

    checkVisits.doGet(httpRequest, httpResponse);
    assertTrue(stringWriter.toString().contains("exampleClassName"));
    assertTrue(stringWriter.toString().contains("20"));
  }

  @Test
  // Lists of names and visits should correspond with multiple classes
  public void checkVisitsForMultipleClasses() throws Exception {
    ArrayList<String> listOfClassNames = new ArrayList<String>();
    ArrayList<Long> visitsPerClass = new ArrayList<Long>();

    // Create two example classes
    Entity visitEntity = new Entity("Visit");
    visitEntity.setProperty("classKey", "testClass101");
    visitEntity.setProperty("numVisits", 15);
    visitEntity.setProperty("className", "exampleClassName");

    Entity visitEntity2 = new Entity("Visit");
    visitEntity2.setProperty("classKey", "testClass103");
    visitEntity2.setProperty("numVisits", 34);
    visitEntity2.setProperty("className", "exampleClass2");

    // Store into datastore
    datastore.put(visitEntity);
    datastore.put(visitEntity2);

    // Obtain visits from datastore and filter them into results query
    Query query = new Query("Visit");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    // Store the class name and number of visits into two separate lists
    for (Entity entity : results.asIterable()) {
      String className = (String) entity.getProperty("className");
      long classVisits = (long) entity.getProperty("numVisits");

      listOfClassNames.add(className);
      visitsPerClass.add(classVisits);
    }

    assertEquals((String) "exampleClassName", (String) listOfClassNames.get(0));
    assertEquals((long) 15, (long) visitsPerClass.get(0));
    assertEquals((String) "exampleClass2", (String) listOfClassNames.get(1));
    assertEquals((long) 34, (long) visitsPerClass.get(1));
  }

  @Test
  // If there are no classes, no visits should be stored
  public void noClasses() throws Exception {
    ArrayList<String> listOfClassNames = new ArrayList<String>();
    ArrayList<Long> visitsPerClass = new ArrayList<Long>();

    // Obtain visits from datastore and filter them into results query
    Query query = new Query("Visit");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    // Store the class name and number of visits into two separate lists
    for (Entity entity : results.asIterable()) {
      String className = (String) entity.getProperty("className");
      long classVisits = (long) entity.getProperty("numVisits");

      listOfClassNames.add(className);
      visitsPerClass.add(classVisits);
    }

    assertTrue(listOfClassNames.isEmpty());
    assertTrue(visitsPerClass.isEmpty());
  }

  @Test
  // Multiple Classes w/ JSON Output
  public void checkVisitJSONOutputMultipleClasses() throws Exception {
    ArrayList<String> listOfClassNames = new ArrayList<String>();
    ArrayList<Long> visitsPerClass = new ArrayList<Long>();

    Entity visitEntity = new Entity("Visit");
    visitEntity.setProperty("classKey", "testClass101");
    visitEntity.setProperty("numVisits", 5);
    visitEntity.setProperty("className", "exampleClassName1");

    Entity visitEntity2 = new Entity("Visit");
    visitEntity2.setProperty("classKey", "testClass102");
    visitEntity2.setProperty("numVisits", 10);
    visitEntity2.setProperty("className", "exampleClassName2");

    Entity visitEntity3 = new Entity("Visit");
    visitEntity3.setProperty("classKey", "testClass103");
    visitEntity3.setProperty("numVisits", 20);
    visitEntity3.setProperty("className", "exampleClassName3");

    Entity visitEntity4 = new Entity("Visit");
    visitEntity4.setProperty("classKey", "testClass104");
    visitEntity4.setProperty("numVisits", 30);
    visitEntity4.setProperty("className", "exampleClassName4");

    datastore.put(visitEntity);
    datastore.put(visitEntity2);
    datastore.put(visitEntity3);
    datastore.put(visitEntity4);

    // Obtain visits from datastore and filter them into results query
    Query query = new Query("Visit");
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    // Store the class name and number of visits into two separate lists
    for (Entity entity : results.asIterable()) {
      String className = (String) entity.getProperty("className");
      long classVisits = (long) entity.getProperty("numVisits");

      listOfClassNames.add(className);
      visitsPerClass.add(classVisits);
    }

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(httpResponse.getWriter()).thenReturn(writer);

    checkVisits.doGet(httpRequest, httpResponse);
    assertTrue(stringWriter.toString().contains("exampleClassName1"));
    assertTrue(stringWriter.toString().contains("exampleClassName2"));
    assertTrue(stringWriter.toString().contains("exampleClassName3"));
    assertTrue(stringWriter.toString().contains("exampleClassName4"));
    assertTrue(stringWriter.toString().contains("5"));
    assertTrue(stringWriter.toString().contains("10"));
    assertTrue(stringWriter.toString().contains("20"));
    assertTrue(stringWriter.toString().contains("30"));
  }
}