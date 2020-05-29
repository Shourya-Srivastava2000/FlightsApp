package flightapp;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.transform.Result;
/**
 * Runs queries against a back-end database
 */
public class Query {
  private boolean currentlyLogged;   // checks if the user is currently logged in
  private String user;
  private Map<Integer, List<Itinerary>> itineraryMap = new HashMap<>(); // itinerary - stores intenerary with unique iid
  private Connection conn; // Establishes a Connection to the database

  // The parameters that determine the strength and the length of the hash
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

    private static final String CLEAR_TABLES = "DELETE FROM userInfo; DELETE FROM capacity; DELETE FROM reservations;" +
                                             "DELETE FROM ID";
  private PreparedStatement clearTable;

  private static final String CLEAR_ITINERARY = "DELETE FROM itinerary";
  private PreparedStatement clearItinerary;

  private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
  private PreparedStatement tranCount;




  private static final String CHECK_USERNAME_EXISTS = "SELECT COUNT(*) FROM userInfo A WHERE A.username = ?";
  private PreparedStatement checkIfUsernameExists; 

  private static final String CREATE_ACCOUNT = "INSERT INTO userInfo VALUES (?, ?, ?, ?)";
  private PreparedStatement storeUserInfo;

  private static final String GET_SALT = "SELECT A.salt FROM userInfo A WHERE A.username = ?";
  private PreparedStatement getSalt;

  private static final String GET_PASSWORD_HASH = "SELECT A.hashedPassword FROM userInfo A WHERE A.username = ?";
  private PreparedStatement getHashedPassword;



  private static final String GET_DIRECT_FLIGHTS = "SELECT TOP (?) F.fid AS fid, F.day_of_month AS day_of_month, " 
                + "F.carrier_id AS carrier_id, F.flight_num AS flight_num, F.origin_city AS origin_city, " +
                  "F.dest_city AS dest_city, F.actual_time AS actual_time, F.capacity AS capacity, F.price AS price " +
                  "FROM Flights F " +
                  "WHERE F.origin_city = ? AND F.dest_city = ? AND F.day_of_month = ? " +
                  "AND F.canceled = 0" +
                  "ORDER BY F.actual_time, F.fid"; 


  private PreparedStatement getDirectFlights;

  private static final String GET_ONE_STOP_FLIGHTS = "SELECT TOP (?) F1.flight_num AS f1_flight_num, " 
                            +"F1.origin_city AS f1_origin_city, F1.dest_city AS f1_dest_city, F1.actual_time AS f1_actual_time, " +
                            "F1.carrier_id AS f1_carrier_id, F1.day_of_month AS f1_day_of_month, F1.fid AS f1_fid, " +
                            "F1.capacity AS f1_capacity, F1.price AS f1_price, F2.flight_num AS f2_flight_num, F2.origin_city AS f2_origin_city, " +
                            "F2.dest_city AS f2_dest_city, F2.actual_time AS f2_actual_time, F2.carrier_id AS f2_carrier_id, " +
                            "F2.day_of_month AS f2_day_of_month, F2.fid AS f2_fid, F2.capacity AS f2_capacity, F2.price AS f2_price, " +
                            "(F1.actual_time + F2.actual_time) AS total_time " +
                            "FROM Flights AS F1 JOIN Flights AS F2 on F1.dest_city = F2.origin_city AND F1.day_of_month = F2.day_of_month " +
                            "WHERE F1.day_of_month = ? AND F1.origin_city = ? AND F2.dest_city = ? AND " +
                            "F1.canceled = 0 AND F2.canceled = 0 " +
                            "ORDER BY F1.actual_time + F2.actual_time, F1.fid, F2.fid ";
  
  private PreparedStatement getOneStopFlight;


  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacity;

  private static final String COUNT_RESERVATIONS_ON_DAY = "SELECT COUNT(*) FROM reservations R WHERE R.day = ? AND " +
                                                          "R.username = ?";
  private PreparedStatement sameDayReservations;

  private static final String COUNT_CAPACITY_EXISTS = "SELECT COUNT(*) FROM capacity C WHERE C.fid = ?";
  private PreparedStatement checkIfCapacityExists;

  private static final String INSERT_NEW_CAPACITY = "INSERT INTO capacity VALUES (?, 1)";
  private PreparedStatement addCapacity;

  private static final String GET_CAPACITY = "SELECT C.numberOfBooked FROM capacity C WHERE C.fid = ?";
  private PreparedStatement getCapacity;

  private static final String UPDATE_CAPACITY = "UPDATE capacity SET numberOfBooked = numberOfBooked + 1 WHERE fid = ?";
  private PreparedStatement updateCapacity;

  private static final String COUNT_ID = "SELECT COUNT(*) FROM ID I";
  private PreparedStatement countID;

  private static final String INSERT_ID = "INSERT INTO ID VALUES (1)";
  private PreparedStatement insertID;

  private static final String CHANGE_ID = "UPDATE ID SET ID = ID + 1";
  private PreparedStatement changeID;

  private static final String GET_ID = "SELECT I.ID from ID I";
  private PreparedStatement getID;

  private static final String INSERT_RESERVATION = "INSERT INTO reservations VALUES " +
          "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private PreparedStatement addReservation;

  private static final String CHECK_RESERVATION = "SELECT COUNT(*) AS cnt, R.username FROM reservations R " +
                                                  "WHERE R.rid = ? AND R.paid = 0 AND R.cancelled = 0" +
                                                  "GROUP BY R.username HAVING R.username = ?";
  private PreparedStatement viewReservation;

  private static final String GET_RESERVATION_COST = "SELECT R.price FROM reservations R WHERE R.rid = ?";
  private PreparedStatement getReservationCost;

  private static final String GET_ACCOUNT_BALANCE = "SELECT A.balance FROM userInfo A WHERE A.username = ?";
  private PreparedStatement getUserBalance;

  private static final String PAY_RESERVATION = "UPDATE reservations SET paid = 1 WHERE rid = ?";
  private PreparedStatement payForReservation;

  private static final String ADD_TO_BALANCE = "UPDATE userInfo SET balance = balance + ? WHERE username = ?";
  private PreparedStatement addToBalance;

  private static final String GET_RESERVATIONS = "SELECT R.rid AS rid, R.fid AS fid, R.day AS day_of_month, " +
                                                  "R.carrier_id AS carrier_id, R.flight_num AS flight_num, R.origin AS origin_city, " +
                                                  "R.dest AS dest_city, R.duration AS actual_time, R.capacity AS capacity, " +
                                                  "R.price AS price, R.paid AS paid, R.direct AS direct " +
                                                  "FROM reservations R " +
                                                  "WHERE R.cancelled = 0 AND R.username = ?";
  private PreparedStatement getReservation;

  private static final String HAS_ANY_RESERVATION = "SELECT COUNT(*) FROM reservations R WHERE R.username = ?";
  private PreparedStatement checkIfReservation;

  //for this one, we already checked that given rid belongs to current username in canceled()
  private static final String CANCEL_RESERVATION = "UPDATE reservations SET cancelled = 1 WHERE rid = ?";
  private PreparedStatement cancelReservation;

  public Query() throws SQLException, IOException {
    this(null, null, null, null);
    this.currentlyLogged = false;
  }

  protected Query(String serverURL, String dbName, String adminName, String password)
          throws SQLException, IOException {
    conn = serverURL == null ? openConnectionFromDbConn()
            : openConnectionFromCredential(serverURL, dbName, adminName, password);

    prepareStatements();
  }

  /**
   * Return a connecion by using dbconn.properties file
   *
   * @throws SQLException
   * @throws IOException
   */
  public static Connection openConnectionFromDbConn() throws SQLException, IOException {
    // Establishes the required connection to the database.
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("hw5.server_url");
    String dbName = configProps.getProperty("hw5.database_name");
    String adminName = configProps.getProperty("hw5.username");
    String password = configProps.getProperty("hw5.password");
    return openConnectionFromCredential(serverURL, dbName, adminName, password);
  }

  /**
   * Return a connecion by using the provided parameter.
   *
   * @param serverURL example: example.database.widows.net
   * @param dbName    database name
   * @param adminName username to login server
   * @param password  password to login server
   *
   * @throws SQLException
   */
  protected static Connection openConnectionFromCredential(String serverURL, String dbName,
                                                           String adminName, String password) throws SQLException {
    String connectionUrl =
            String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
                    dbName, adminName, password);
    Connection conn = DriverManager.getConnection(connectionUrl);
    conn.setAutoCommit(true); //commits automatically after each statement
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE); //sets default isolation level as serializable
    return conn;
  }

  /**
   * Get underlying connection
   */
  public Connection getConnection() {
    return conn;
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearTable.executeUpdate();
      clearTable.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    checkFlightCapacity = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    tranCount = conn.prepareStatement(TRANCOUNT_SQL);
    clearTable = conn.prepareStatement(CLEAR_TABLES);
    clearItinerary = conn.prepareStatement(CLEAR_ITINERARY);
    checkIfUsernameExists = conn.prepareStatement(CHECK_USERNAME_EXISTS);
    storeUserInfo = conn.prepareStatement(CREATE_ACCOUNT);
    getSalt = conn.prepareStatement(GET_SALT);
    getHashedPassword = conn.prepareStatement(GET_PASSWORD_HASH);
    getDirectFlights = conn.prepareStatement(GET_DIRECT_FLIGHTS, ResultSet.TYPE_SCROLL_INSENSITIVE,  ResultSet.CONCUR_READ_ONLY);
    getOneStopFlight = conn.prepareStatement(GET_ONE_STOP_FLIGHTS, ResultSet.TYPE_SCROLL_INSENSITIVE,  ResultSet.CONCUR_READ_ONLY);
    sameDayReservations = conn.prepareStatement(COUNT_RESERVATIONS_ON_DAY);
    addCapacity = conn.prepareStatement(INSERT_NEW_CAPACITY);
    getCapacity = conn.prepareStatement(GET_CAPACITY);
    addReservation = conn.prepareStatement(INSERT_RESERVATION);
    checkIfCapacityExists = conn.prepareStatement(COUNT_CAPACITY_EXISTS);
    viewReservation = conn.prepareStatement(CHECK_RESERVATION);
    getReservationCost = conn.prepareStatement(GET_RESERVATION_COST);
    getUserBalance = conn.prepareStatement(GET_ACCOUNT_BALANCE);
    payForReservation = conn.prepareStatement(PAY_RESERVATION);
    addToBalance = conn.prepareStatement(ADD_TO_BALANCE);
    getReservation = conn.prepareStatement(GET_RESERVATIONS, ResultSet.TYPE_SCROLL_INSENSITIVE,  ResultSet.CONCUR_READ_ONLY);
    checkIfReservation = conn.prepareStatement(HAS_ANY_RESERVATION);
    cancelReservation = conn.prepareStatement(CANCEL_RESERVATION);
    updateCapacity = conn.prepareStatement(UPDATE_CAPACITY);
    changeID = conn.prepareStatement(CHANGE_ID);
    countID = conn.prepareStatement(COUNT_ID);
    insertID = conn.prepareStatement(INSERT_ID);
    getID = conn.prepareStatement(GET_ID);

  }

  // input password, salt, returns salted password's hash
  private byte[] generateHash(String password, byte[] salt) {
    // Specify the hash parameters
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);

    // Generate the hash
    SecretKeyFactory factory = null;
    byte[] hash = null;
    try {
      factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      return factory.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      throw new IllegalStateException();
    }
  }

  private byte[] generateSalt() {
    // Generate a random cryptographic salt
    SecureRandom random = new SecureRandom();
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    return salt;
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    try {
      if (currentlyLogged) {
        return "User already logged in\n";
      } else {
        checkIfUsernameExists.setString(1, username);
        ResultSet answer = checkIfUsernameExists.executeQuery();
        answer.next();
        if (answer.getInt(1) == 1) { 
          getSalt.setString(1, username);
          ResultSet saltedSet = getSalt.executeQuery();
          saltedSet.next();
          byte[] salt = saltedSet.getBytes(1);
          byte[] attemptedPasswordHash = generateHash(password, salt); 
          getHashedPassword.setString(1, username); 
          ResultSet results = getHashedPassword.executeQuery();
          results.next();

          if (Arrays.equals(attemptedPasswordHash, results.getBytes(1))) { 
            this.currentlyLogged = true;
            answer.close(); 
            saltedSet.close(); 
            results.close();
            this.user = username;
            return String.format("Logged in as %s\n", username);
          } else {
            answer.close(); saltedSet.close(); results.close();
            return "Login failed\n";
          }
        }
        answer.close();
        return "Login failed\n"; //not logged in & username invalid
      }
    } catch (SQLException e) {
      return "Login failed\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    for (int i = 0; i < 3; i++) {
      try {
        conn.setAutoCommit(false);
        checkIfUsernameExists.setString(1, username);
        ResultSet answer = checkIfUsernameExists.executeQuery();
        answer.next();
        if (initAmount >= 0 && answer.getInt(1) == 0) { 
          byte[] salt = generateSalt();
          byte[] hashedPassword = generateHash(password, salt);
          storeUserInfo.setString(1, username);
          storeUserInfo.setBytes(2, hashedPassword);
          storeUserInfo.setBytes(3, salt);
          storeUserInfo.setInt(4, initAmount);
          storeUserInfo.executeUpdate();
          answer.close();
          conn.commit();
          conn.setAutoCommit(true);
          return String.format("Created user %s\n", username);
        } else {
          answer.close();
          conn.setAutoCommit(true);
          return "Failed to create user\n";
        }
      } catch (SQLException e) {
        try {
          if (checkDeadlock(e)) {
            conn.rollback();
          }
          conn.setAutoCommit(true);
        } catch (SQLException se) {
          e.printStackTrace();
        }
      } finally {
        checkDanglingTransaction();
      }
    }
    return "Failed to create user\n";
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
   * is searches for direct flights and flights with two "hops." Only searches for up to the number
   * of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight,
                                   int dayOfMonth, int numberOfItineraries) {
    try {
      // WARNING the below code is unsafe and only handles searches for direct flights
      // You can use the below code as a starting reference point or you can get rid
      // of it all and replace it with your own implementation.

      StringBuffer sb = new StringBuffer();
      try {
        itineraryMap = new HashMap<>();
        getDirectFlights.setInt(1, numberOfItineraries);
        getDirectFlights.setString(2, originCity);
        getDirectFlights.setString(3, destinationCity);
        getDirectFlights.setInt(4, dayOfMonth);
        ResultSet directFlights = getDirectFlights.executeQuery();

        int count = 0;
        if (directFlight || (!directFlight && getResultSetSize(directFlights) == numberOfItineraries)) { // direct only, one call
          while (directFlights.next()) {
            addDirectFlightsToBuffer(sb, directFlights, count);
            count++;
          }
        } else { 
          getOneStopFlight.setInt(1, numberOfItineraries);
          getOneStopFlight.setInt(2, dayOfMonth);
          getOneStopFlight.setString(3, originCity);
          getOneStopFlight.setString(4, destinationCity);
          ResultSet indirectFlightSet = getOneStopFlight.executeQuery();

          int maxIndirect = numberOfItineraries - getResultSetSize(directFlights);
          int counter = 0;
          while (count != numberOfItineraries && (directFlights.next() || indirectFlightSet.next())) {
            directFlights.previous();
            indirectFlightSet.previous();
            if (counter != maxIndirect &&  quickestItenerary(directFlights, indirectFlightSet) > 0) {
              addIndirectFlightsToBuffer(sb, indirectFlightSet, count);
              indirectFlightSet.next(); 
              counter++; 
              count++; 
            } else { 
              addDirectFlightsToBuffer(sb, directFlights, count);
              directFlights.next();
              count++; 
            }
          }
          indirectFlightSet.close();
        }
        directFlights.close();
        
        if (count == 0) {
          return "No flights match your selection\n";
        } else {
          return sb.toString();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
      return sb.toString();1
    } finally {
      checkDanglingTransaction();
    }
  }

  
  private void addDirectFlightsToBuffer(StringBuffer sb, ResultSet rs, int itineraryID) {
    try {
      int flightID = rs.getInt("fid");
      int dayOfMonth = rs.getInt("day_of_month");
      String carrierID = rs.getString("carrier_id");
      int flightNum = rs.getInt("flight_num");
      String originCity = rs.getString("origin_city");
      String destCity = rs.getString("dest_city");
      int time = rs.getInt("actual_time");
      int capacity = rs.getInt("capacity");
      int price = rs.getInt("price");

      Flight f = new Flight(flightID, dayOfMonth, carrierID, flightNum, originCity,
              destCity, time, capacity, price);
      sb.append("Itinerary " + itineraryID + ": 1 flight(s), " + time + " minutes\n");
      sb.append(f.toString() + "\n");
      //add to itinerary table
      //iid, fid, day, cid, flightNum, origin, dest, duration, capacity, price
      List<Itinerary> list = new ArrayList<>();
      list.add(new Itinerary(flightID, dayOfMonth, carrierID, flightNum, originCity, destCity,
              time, capacity, price));
      itineraryMap.put(itineraryID, list);
      } catch (SQLException e) {
        e.printStackTrace();
      }
  }

  //add indirect flight itinerary to given string buffer
  private void addIndirectFlightsToBuffer(StringBuffer sb, ResultSet rs, int iid) {
    try {
        int f1_flightID = rs.getInt("f1_fid");
        int f1_dayOfMonth = rs.getInt("f1_day_of_month");
        String f1_carrierID = rs.getString("f1_carrier_id");
        int f1_flightNum = rs.getInt("f1_flight_num");
        String f1_originCity = rs.getString("f1_origin_city");
        String f1_destCity = rs.getString("f1_dest_city");
        int f1_time = rs.getInt("f1_actual_time");
        int f1_capacity = rs.getInt("f1_capacity");
        int f1_price = rs.getInt("f1_price");

        int f2_flightID = rs.getInt("f2_fid");
        int f2_dayOfMonth = rs.getInt("f2_day_of_month");
        String f2_carrierID = rs.getString("f2_carrier_id");
        int f2_flightNum = rs.getInt("f2_flight_num");
        String f2_originCity = rs.getString("f2_origin_city");
        String f2_destCity = rs.getString("f2_dest_city");
        int f2_time = rs.getInt("f2_actual_time");
        int f2_capacity = rs.getInt("f2_capacity");
        int f2_price = rs.getInt("f2_price");
        int total_time = rs.getInt("total_time");

        Flight f1 = new Flight(f1_flightID, f1_dayOfMonth, f1_carrierID, f1_flightNum, f1_originCity,
                               f1_destCity, f1_time, f1_capacity, f1_price);
        Flight f2 = new Flight(f2_flightID, f2_dayOfMonth, f2_carrierID, f2_flightNum, f2_originCity,
                               f2_destCity, f2_time, f2_capacity, f2_price);
        sb.append("Itinerary " + iid + ": 2 flight(s), " + total_time + " minutes\n");
        sb.append(f1.toString() + "\n");
        sb.append(f2.toString() + "\n");
        //add to itinerary table
        Itinerary it1 = new Itinerary(f1_flightID, f1_dayOfMonth, f1_carrierID, f1_flightNum, f1_originCity,
                f1_destCity, f1_time, f1_capacity, f1_price);
        Itinerary it2 = new Itinerary(f2_flightID, f2_dayOfMonth, f2_carrierID, f2_flightNum, f2_originCity,
                f2_destCity, f2_time, f2_capacity, f2_price);
        List<Itinerary> list = new ArrayList<>();
        list.add(it1);
        list.add(it2);
        itineraryMap.put(iid, list);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  
    // checks if the user has a booking on the same day
    private boolean hasBookingonDay(int itineraryId) {
      try {
        int dayOfItinerary = itineraryMap.get(itineraryId).get(0).day;
        sameDayReservations.setInt(1, dayOfItinerary);
        sameDayReservations.setString(2, this.user);
        ResultSet resultSet = sameDayReservations.executeQuery();
        resultSet.next();
        return resultSet.getInt(1) != 0; // return true if count of booked flights not zero
      } catch (SQLException e) {
        e.printStackTrace();
        return true;
      }
    }
  // insert the itinerary into the reservations table
  private void addToReservation(Itinerary i, int rid, String username, int direct) {
    try {
      int fid = i.fid;
      int day = i.day;
      String cid = i.carrier_ID;
      int flightNum = i.flightNum;
      String origin = i.origin;
      String dest = i.dest;
      int duration = i.duration;
      int capacity = i.capacity;
      int price = i.price;

      conn.setAutoCommit(false);
      
      addReservation.setInt(1, rid);
      addReservation.setString(2, username);
      addReservation.setInt(3, fid);
      addReservation.setInt(4, day);
      addReservation.setString(5, cid);
      addReservation.setInt(6, flightNum);
      addReservation.setString(7, origin);
      addReservation.setString(8, dest);
      addReservation.setInt(9, duration);
      addReservation.setInt(10, capacity);
      addReservation.setInt(11, price);
      addReservation.setInt(12, 0); 
      addReservation.setInt(13, 0); 
      addReservation.setInt(14, direct); 
      addReservation.executeUpdate();

      conn.commit();
      conn.setAutoCommit(true);
    } catch (SQLException e) {
      e.printStackTrace();
      try {
        if (checkDeadlock(e)) {
          conn.rollback();
        }
        conn.setAutoCommit(true);
      } catch (SQLException se) {
        se.printStackTrace();
      }
    }
  }
  
  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *                    the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   *         If the user is trying to book an itinerary with an invalid ID or without having done a
   *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
   *         a reservation on the same day as the one that they are trying to book now, then return
   *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
   *         failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from 1 and
   *         increments by 1 each time a successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    for (int j = 0; j < 3; j++) {
      try {
        if (!currentlyLogged) {
          return "Cannot book reservations, not logged in\n";
        } else if (itineraryMap.isEmpty() || !itineraryMap.containsKey(itineraryId)) {
          return String.format("No such itinerary %d\n", itineraryId);
        } else if (hasBookingonDay(itineraryId)) {
          return "You cannot book two flights in the same day\n";
        }
        conn.setAutoCommit(false);
        List<Itinerary> listOfItinerary = itineraryMap.get(itineraryId);
        int direct = listOfItinerary.size() % 2;
        for (Itinerary i : listOfItinerary) {
          int capacity = i.capacity;
          int fid = i.fid;
          int numberOfBooked = getItenCapacity(fid);
          if (capacity == numberOfBooked) {
            conn.setAutoCommit(true);
            return "Booking failed\n";
          }
        }
        for (Itinerary i : listOfItinerary) {
          int fid = i.fid;
          if (checkInCapacity(fid)) {
            //increment numberOfBooked
           updateCapacity.setInt(1, fid);
           updateCapacity.executeUpdate();
          } else {
            addCapacity.setInt(1, fid);
            addCapacity.executeUpdate();
          }
        }
        if (isIDEmpty()) {
          insertID.executeUpdate();
        } else {
          changeID.executeUpdate();
        }
        ResultSet resultSet = getID.executeQuery();
        resultSet.next();
        int ID = resultSet.getInt(1);
        for (Itinerary i : listOfItinerary) {
          addToReservation(i, ID, this.user, direct);
        }

        conn.commit();
        conn.setAutoCommit(true);
        return String.format("Booked flight(s), reservation ID: %d\n", ID);
      } catch (SQLException e) {
        e.printStackTrace();
        try {
          if (checkDeadlock(e)) {
            conn.rollback();
          }
          conn.setAutoCommit(true);
        } catch (SQLException se) {
          se.printStackTrace();
        }
      } finally {
        checkDanglingTransaction();
      }
    }
    return "Booking failed\n";
  }
  // checks if the flights are already in the capacity table
  private boolean checkInCapacity(int fid) {
    try {
      checkIfCapacityExists.setInt(1, fid);
      ResultSet answer = checkIfCapacityExists.executeQuery();
      answer.next();
      return answer.getInt(1) == 1;
    } catch (SQLException e) {
      return false;
    }
  }

  // returns if the given iid has capacity left
  private int getItenCapacity(int iid) {
    try {
      getCapacity.setInt(1, iid);
      ResultSet answer = getCapacity.executeQuery();
      if (!answer.next()) { 
        return 0;
      } else {
        return answer.getInt(1);
      }
    } catch (SQLException e) {
      return -1;
    }
  }

  


  //return true if ID table is empty
  private boolean isIDEmpty() {
    try {
      ResultSet answer = countID.executeQuery();
      answer.next();
      return answer.getInt(1) == 0;
    } catch (SQLException e) {
      return true;
    }
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   *         is not found / not under the logged in user's name, then return "Cannot find unpaid
   *         reservation [reservationId] under user: [username]\n" If the user does not have enough
   *         money in their account, then return "User has only [balance] in account but itinerary
   *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
   *         [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    for (int i = 0; i < 3; i++) {
      try {
        if (!currentlyLogged) {
          return "Cannot pay, not logged in\n";
        }
        int balance = getBalance(this.user);
        int price = getPrice(reservationId);
        if (!checkReservation(reservationId, this.user)) {
          return String.format("Cannot find unpaid reservation %d under user: %s\n", reservationId, this.user);
        } else if (balance < price) {
          return String.format("User has only %d in account but itinerary costs %d\n", balance, price);
        }
        conn.setAutoCommit(false);
        payForReservation.setInt(1, reservationId);
        payForReservation.executeUpdate();

        addToBalance.setInt(1, -1 * price); 
        addToBalance.setString(2, this.user);
        addToBalance.executeUpdate();
        conn.commit();
        conn.setAutoCommit(true);
        return  String.format("Paid reservation: %d remaining balance: %d\n", reservationId, balance - price);
      } catch (SQLException e) {
        try {
          e.printStackTrace();
          if (checkDeadlock(e)) {
            conn.rollback();
          }
          conn.setAutoCommit(true);
        } catch (SQLException se) {
          return String.format("Failed to pay for reservation %d\n", reservationId);
        }
      } finally {
        checkDanglingTransaction();
      }
    }
    return String.format("Failed to pay for reservation %d\n", reservationId);
  }

  // checks for reservations
  private boolean checkReservation(int reservationId, String username) {
    try {
      viewReservation.setInt(1, reservationId);
      viewReservation.setString(2, username);
      ResultSet answer = viewReservation.executeQuery();
      return answer.next(); 
    } catch (SQLException e) {
      return false;
    }
  }

  // gets the balance of te users account
  private int getBalance(String username) {
    try {
      getUserBalance.setString(1, username);
      ResultSet answer = getUserBalance.executeQuery();
      answer.next();
      int balance = answer.getInt(1);
      return balance;
    } catch (SQLException e) {
      return -1;
    }
  }

  private int getPrice(int rid) {
    try {
      getReservationCost.setInt(1, rid);
      ResultSet resultSet = getReservationCost.executeQuery();
      int price = 0;
      while (resultSet.next()) {
        price += resultSet.getInt(1);
      }
      return price;
    } catch (SQLException e) {
      return -1;
    }
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    for (int i = 0; i < 3; i++){
      try {
        if (!currentlyLogged) {
          return "Cannot view reservations, not logged in\n";
        } else if (!hasAnyReservation(this.user)) {
          return "No reservations found\n";
        }

        conn.setAutoCommit(false);
        StringBuffer sb = new StringBuffer();
        getReservation.setString(1, this.user);
        ResultSet answer = getReservation.executeQuery();
        
        while (answer.next()) {
          int type = answer.getInt("direct");
          int paid = answer.getInt("paid");
          int rid = answer.getInt("rid");
          
          if (type == 1) { 
            sb.append("Reservation " + rid + " paid: " + (paid == 1) + ":\n");
            addReservationToBuffer(sb, answer);
          } else { 
            sb.append("Reservation " + rid + " paid: " + (paid == 1) + ":\n");
            addReservationToBuffer(sb, answer); 
            answer.next();
            addReservationToBuffer(sb, answer); 
          }
        }

        conn.commit();
        conn.setAutoCommit(true);
        
        return sb.toString();
      } catch (SQLException e) {
        e.printStackTrace();
        try {
          if (checkDeadlock(e)) {
            conn.rollback();
          }
          conn.setAutoCommit(true);
        } catch (SQLException se) {
          return "Failed to retrieve reservations\n";
        }
      } finally {
        checkDanglingTransaction();
      }
    }
    return "Failed to retrieve reservations\n";
  }


  private void addReservationToBuffer(StringBuffer sb, ResultSet rs) {
    try {
      int flightID = rs.getInt("fid");
      int dayOfMonth = rs.getInt("day_of_month");
      String carrierID = rs.getString("carrier_id");
      int flightNum = rs.getInt("flight_num");
      String originCity = rs.getString("origin_city");
      String destCity = rs.getString("dest_city");
      int time = rs.getInt("actual_time");
      int capacity = rs.getInt("capacity");
      int price = rs.getInt("price");
      Flight f = new Flight(flightID, dayOfMonth, carrierID, flightNum, originCity,
              destCity, time, capacity, price);
      sb.append(f.toString() + "\n");
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  
  private boolean hasAnyReservation(String username) {
    try {
      checkIfReservation.setString(1, username);
      ResultSet rs = checkIfReservation.executeQuery();
      rs.next();
      return rs.getInt(1) != 0; 
    } catch (SQLException e) {
      return false;
    }
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *         all other errors, return "Failed to cancel reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    for (int i = 0; i < 3; i++) {
      try {
          if (!currentlyLogged) {
            return "Cannot cancel reservations, not logged in\n";
          }
          if (checkReservation(reservationId, this.user)) { //true if rid belongs to username's account
            conn.setAutoCommit(false);
            int price = getPrice(reservationId);
            addToBalance.setInt(1, price);
            addToBalance.setString(2, this.user);
            addToBalance.executeUpdate();
            cancelReservation.setInt(1, reservationId);
            cancelReservation.executeUpdate();
            conn.commit();
            conn.setAutoCommit(true);
            return String.format("Canceled reservation %d\n", reservationId);
          }
      } catch (SQLException e) {
        e.printStackTrace();
        try {
          if (checkDeadlock(e)) {
            conn.rollback();
          }
          conn.setAutoCommit(true);
        } catch(SQLException se) {
          return "Failed to cancel reservation " + reservationId + "\n";
        }
      } finally {
        checkDanglingTransaction();
      }
    }
    return "Failed to cancel reservation " + reservationId + "\n";
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int flightCapacityChecker(int fid) throws SQLException {
    checkFlightCapacity.clearParameters();
    checkFlightCapacity.setInt(1, fid);
    ResultSet results = checkFlightCapacity.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();
    return capacity;
  }

  /**
   * Throw IllegalStateException if transaction not completely complete, rollback.
   *
   */
  private void checkDanglingTransaction() {
    try {
      try (ResultSet rs = tranCount.executeQuery()) {
        rs.next();
        int count = rs.getInt("tran_count");
        if (count > 0) {
          throw new IllegalStateException(
                  "Transaction not fully commit/rollback. Number of transaction in process: " + count);
        }
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database error", e);
    }
  }

  private static boolean checkDeadlock(SQLException ex) {
    return ex.getErrorCode() == 1205;
  }
  
  // returns the size of the Result Set
  private int getResultSetSize(ResultSet rs) {
    try {
      int size = 0;
      if (rs != null) {
        rs.beforeFirst();
        rs.last();
        size = rs.getRow();
        rs.beforeFirst();
      }
      return size;
    } catch (SQLException e) {
      return -1;
    }
  }


  private int quickestItenerary(ResultSet directFlights, ResultSet indirectFlights) {
    try {
      if (!directFlights.next()) { 
        return 1;
      } else if (!indirectFlights.next()) { 
        return -1;
      } else {
        int durationDirect = directFlights.getInt("actual_time");
        int durationIndirect = indirectFlights.getInt("total_time");
        if (durationDirect != durationIndirect) {
          return durationDirect - durationIndirect;
        } else { 
          int FID1ForIndirect = indirectFlights.getInt("f1_fid");
          int FID2ForIndirect = indirectFlights.getInt("f2_fid");
          int FIDfordirect = directFlights.getInt("fid");

          if (FIDfordirect != FID1ForIndirect) {
            return FIDfordirect - FID1ForIndirect;
          } else {
            return FIDfordirect - FID2ForIndirect;
          }
        }
      }
    } catch (SQLException e) {
      return -1;
    }
  }



  class Itinerary {
    public int fid;
    public int day;
    public String carrier_ID;
    public int flightNum;
    public String origin;
    public String dest;
    public int duration;
    public int capacity;
    public int price;

    public Itinerary(int fid, int day, String cid, int flightNum, String origin, String dest,
                     int duration, int capacity, int price) {
      this.fid = fid;
      this.day = day;
      this.carrier_ID = cid;
      this.flightNum = flightNum;
      this.origin = origin;
      this.dest = dest;
      this.duration = duration;
      this.capacity = capacity;
      this.price = price;
    }
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public int flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    public Flight(int fid, int day, String carrierID, int flightNum, String origin, String dest,
                  int time, int capacity, int price) {
      this.fid = fid;
      this.dayOfMonth = day;
      this.carrierId = carrierID;
      this.flightNum = flightNum;
      this.originCity = origin;
      this.destCity = dest;
      this.time = time;
      this.capacity = capacity;
      this.price = price;
    }

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
              + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
              + " Capacity: " + capacity + " Price: " + price;
    }
  }
}

