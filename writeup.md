This repo contains the java code for the backend of a flight-booking service. 

It achieves two main goals:
  1. Design a database of your customers and the flights they book
  2. Completes a working prototype of your flight booking application that connects to the database (in Azure) then allows customers to       use a CLI to search, book, cancel, etc. flights.

This service is based on the tables Flights, Carriers, Months, and Weekdays.
The application does not modify the data in these tables, as it only pertains to flight booking and not to scheduling. 
Users interact with your service by running the client application.

The client application can either:

  store information locally (just inside the client program) and transiently (not saved after the program closes), in which case this information does not need to be stored in a server database.
  or, store information globally (accessible to all client programs) and persistently (saved after the program closes),
in which case this information needs to be stored in a server database.



Users: A user has a username (varchar), password (varbinary), and balance (int) in their account.
All usernames are unique in the system. Each user can have any number of reservations.
Usernames are case insensitive (this is the default for SQL Server).
Passwords are case sensitive as they are being salted and hashed through a java application. 
You can assume that all usernames and passwords have at most 20 characters.


Itineraries: An itinerary is either a direct flight (consisting of one flight: origin --> destination) or
a one-hop flight (consisting of two flights: origin --> stopover city, stopover city --> destination). Itineraries are returned by the search command.

Reservations: A booking for an itinerary, which may consist of one (direct) or two (one-hop) flights.
Each reservation can either be paid or unpaid, cancelled or not, and has a unique ID.



Below, I have justified the design I chose for the database vis.a.vis creating new tables. 

All tables are stored in the server, as they have aspects not local to a particular user. 

### Reservations
The reservation table holds all information required for the output of the transaction_reservation() method.
The attributes of the table and their roles are listed below:
- rid [type: INT]: Keeps track of all reservations by giving them an id number, not the primary key so as to incorporate indirect itenraries.  
- username [type: varchar(20)]: It is essential to have the username in the reservation table paying or editing other users reservations. 
- fid [type: INT]
- day [type: INT]: The day of departure 
- carrier_id [type VARCHAR]:  Identical to the CIDs from carrier, used  to identify carriers
- flight_num [type: INT]: Identical to the FIDs from flight, used  to identify flights
- origin [type VARCHAR]: stores the origin city
- dest [type: VARCHAR]: Stores the destination city
- duration [type: INT]: Displays the duration of the trip.
- capacity [type: INT]: To display the number of seats/capacity of a particular itenerary. 
- price [type: INT]: To display the price of the itenrary
- paid [type: INT]: To see if a reservation is paid or unpaid, 1 if paid, 0 is unpaid.
- cancelled [type: INT]: To see if a reservation is cancelled or not, 1 if  cancelled, 0 if current.
- direct [type: INT]: To check if an itenrary is direct, 1 for direct and 0 for indirect

### userInfo
- username [type = VARCHAR]: is the primary key as no two users can have the same username
- hashedPassword [type = VARBINARY]: stores the *hashed* password, so that the original password as plain text.
- salt [type = VARBINARY]: the salt that is added to the pasowrd for one given user. (not the most secure way of storing the hash)
- balance [type = INT]: the balance the users account has

### ID
The ID table always has a singular table, which keeps track of the next reservation ID that is going to be used. It has one single field of type INT to keep track of the rids. 


### Note
I have not made a table for the itenararies the search() returns, as they do not need to be stored in the server because: 
- the searches are unqique to every user
- they should be locally stored as they should be deleted after every search.
- If we store them in the itenerary table, we will not be able to identify whch user has which searches stored. 
