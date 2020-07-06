package com.google.sps.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.sps.firebase.FirebaseAppManager;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/enterqueue")
public final class EnterQueue extends HttpServlet {
  private FirebaseAuth authInstance;
  private DatastoreService datastore;
  private Clock clock;

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      authInstance = FirebaseAuth.getInstance(FirebaseAppManager.getApp());
      clock = Clock.systemUTC();
    } catch (IOException e) {
      throw new ServletException(e);
    }
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Get the input from the form.

    datastore = DatastoreServiceFactory.getDatastoreService();
    System.setProperty(
        DatastoreServiceConfig.DATASTORE_EMPTY_LIST_SUPPORT, Boolean.TRUE.toString());

    try {
      String classCode = request.getParameter("classCode").trim();

      String idToken = request.getParameter("idToken");
      FirebaseToken decodedToken = authInstance.verifyIdToken(idToken);
      String userID = decodedToken.getUid();

      if (request.getParameter("enterTA") == null) {
        int retries = 10;
        while (true) {
          TransactionOptions options = TransactionOptions.Builder.withXG(true);
          Transaction txn = datastore.beginTransaction(options);
          try {
            Key classKey = KeyFactory.stringToKey(classCode);
            Entity classEntity = datastore.get(txn, classKey);

            // Get date in mm/dd/yyyy format
            DateTimeFormatter FOMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            LocalDate localDate = LocalDate.now(clock);
            String currDate = FOMATTER.format(localDate);

            Filter classVisitFilter =
                new FilterPredicate("classCode", FilterOperator.EQUAL, classCode);

            Filter dateVisitFilter = new FilterPredicate("date", FilterOperator.EQUAL, currDate);

            CompositeFilter visitFilter =
                CompositeFilterOperator.and(dateVisitFilter, classVisitFilter);

            int retries2 = 10;
            while (true) {
              Transaction txn2 = datastore.beginTransaction();
              try {
                Query checkAddNew = new Query("Visit").setFilter(visitFilter);

                if (datastore.prepare(checkAddNew).countEntities() == 0) {
                  Entity newVisitEntity = new Entity("Visit");

                  newVisitEntity.setProperty("classCode", classCode);
                  newVisitEntity.setProperty("date", currDate);
                  newVisitEntity.setProperty("numVisits", 0);

                  datastore.put(txn2, newVisitEntity);
                }

                txn2.commit();
                break;
              } catch (ConcurrentModificationException e) {
                if (retries2 == 0) {
                  throw e;
                }
                // Allow retry to occur
                --retries2;
              } finally {
                if (txn2.isActive()) {
                  txn2.rollback();
                }
              }
            }

            Query query = new Query("Visit").setFilter(visitFilter);

            Entity visitEntity = datastore.prepare(query).asSingleEntity();
            Long numVisits = (Long) visitEntity.getProperty("numVisits");

            ArrayList<String> updatedQueue = (ArrayList) classEntity.getProperty("studentQueue");

            if (!updatedQueue.contains(userID)) {
              updatedQueue.add(userID);
              numVisits++;
            }

            visitEntity.setProperty("numVisits", numVisits);
            datastore.put(txn, visitEntity);

            classEntity.setProperty("studentQueue", updatedQueue);
            datastore.put(txn, classEntity);

            txn.commit();
            break;
          } catch (ConcurrentModificationException e) {
            if (retries == 0) {
              throw e;
            }
            // Allow retry to occur
            --retries;
          } finally {
            if (txn.isActive()) {
              txn.rollback();
            }
          }
        }
        response.sendRedirect("/queue/student.html?classCode=" + classCode);
      } else {
        Key classKey = KeyFactory.stringToKey(classCode);
        Entity classEntity = datastore.get(classKey);

        List<String> taList = (List<String>) classEntity.getProperty("taList");

        if (taList.contains(userID)) {
          response.sendRedirect("/queue/ta.html?classCode=" + classCode);
        } else {
          response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
      }

    } catch (EntityNotFoundException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (IllegalArgumentException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    } catch (FirebaseAuthException e) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
  }
}
