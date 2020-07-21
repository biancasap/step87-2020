package com.google.sps.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.auth.FirebaseAuth;
import com.google.sps.firebase.FirebaseAppManager;
import com.google.sps.tasks.TaskSchedulerFactory;
import com.google.sps.workspace.WorkspaceFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/delete-class")
public class DeleteClass extends HttpServlet {
  private FirebaseAuth authInstance;
  private DatastoreService datastore;
  private WorkspaceFactory workspaceFactory;
  private TaskSchedulerFactory taskSchedulerFactory;
  @VisibleForTesting protected String QUEUE_NAME;

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      authInstance = FirebaseAuth.getInstance(FirebaseAppManager.getApp());
      taskSchedulerFactory = TaskSchedulerFactory.getInstance();
      workspaceFactory = WorkspaceFactory.getInstance();
      QUEUE_NAME = System.getenv("EXECUTION_QUEUE_ID");
    } catch (IOException e) {
      throw new ServletException(e);
    }
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    datastore = DatastoreServiceFactory.getDatastoreService();
    System.setProperty(
        DatastoreServiceConfig.DATASTORE_EMPTY_LIST_SUPPORT, Boolean.TRUE.toString());

    try {
      String classCode = request.getParameter("classCode").trim();

      // Retrive class entity
      Key classKey = KeyFactory.stringToKey(classCode);
      Entity classEntity = datastore.get(classKey);

      // Delete Class entity
      EmbeddedEntity beingHelped = (EmbeddedEntity) classEntity.getProperty("beingHelped");
      List<EmbeddedEntity> queue = (List<EmbeddedEntity>) classEntity.getProperty("studentQueue");
      datastore.delete(classKey);

      // Delete Visit entities
      Filter classVisitFilter = new FilterPredicate("classKey", FilterOperator.EQUAL, classKey);
      Query visitQuery = new Query("Visit").setFilter(classVisitFilter);

      for (Entity elem : datastore.prepare(visitQuery.setKeysOnly()).asIterable()) {
        datastore.delete(elem.getKey());
      }

      // Delete Wait entities
      Filter classWaitFilter = new FilterPredicate("classKey", FilterOperator.EQUAL, classKey);
      Query waitQuery = new Query("Wait").setFilter(classWaitFilter);

      for (Entity elem : datastore.prepare(waitQuery.setKeysOnly()).asIterable()) {
        datastore.delete(elem.getKey());
      }

      // Delete class key from every user
      Filter registeredClassesFilter =
          new FilterPredicate("registeredClasses", FilterOperator.IN, Arrays.asList(classKey));
      Query registeredClassesQuery = new Query("User").setFilter(registeredClassesFilter);
      for (Entity entity : datastore.prepare(registeredClassesQuery).asIterable()) {
        ArrayList<Key> classes = (ArrayList<Key>) entity.getProperty("registeredClasses");
        classes.remove(classKey);
        entity.setProperty("registeredClasses", classes);
        datastore.put(entity);
      }

      Filter ownedClassesFilter =
          new FilterPredicate("ownedClasses", FilterOperator.IN, Arrays.asList(classKey));
      Query ownedClassesQuery = new Query("User").setFilter(ownedClassesFilter);
      for (Entity entity : datastore.prepare(ownedClassesQuery).asIterable()) {
        ArrayList<Key> classes = (ArrayList<Key>) entity.getProperty("ownedClasses");
        classes.remove(classKey);
        entity.setProperty("ownedClasses", classes);
        datastore.put(entity);
      }

      Filter taClassesFilter =
          new FilterPredicate("taClasses", FilterOperator.IN, Arrays.asList(classKey));
      Query taClassesQuery = new Query("User").setFilter(taClassesFilter);
      for (Entity entity : datastore.prepare(taClassesQuery).asIterable()) {
        ArrayList<Key> classes = (ArrayList<Key>) entity.getProperty("taClasses");
        classes.remove(classKey);
        entity.setProperty("taClasses", classes);
        datastore.put(entity);
      }

      // Schedule environment deletion
      Filter classEnvironFilter = new FilterPredicate("class", FilterOperator.EQUAL, classKey);
      Query environQuery = new Query("Environment").setFilter(classEnvironFilter);

      for (Entity elem : datastore.prepare(environQuery.setKeysOnly()).asIterable()) {
        elem.setProperty("status", "deleting");
        datastore.put(elem);

        taskSchedulerFactory
            .create(QUEUE_NAME, "/tasks/deleteEnv")
            .schedule(KeyFactory.keyToString(elem.getKey()));
      }

      // Delete dangling workspaces in beingHelped
      for (String studentID : beingHelped.getProperties().keySet()) {
        EmbeddedEntity helpInfo = (EmbeddedEntity) beingHelped.getProperty(studentID);
        String workspaceID = (String) helpInfo.getProperty("workspaceID");

        workspaceFactory.fromWorkspaceID(workspaceID).delete();
      }

      // Delete dangling workspaces in queue
      for (EmbeddedEntity student : queue) {
        EmbeddedEntity studentInfo =
            (EmbeddedEntity)
                student.getProperty(
                    (String) student.getProperties().keySet().stream().findFirst().orElse(null));
        String workspaceID = (String) studentInfo.getProperty("workspaceID");

        workspaceFactory.fromWorkspaceID(workspaceID).delete();
      }
    } catch (EntityNotFoundException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (IllegalArgumentException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    } catch (InterruptedException | ExecutionException e) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
