/* CREATE MASTER KEY ENCRYPTION BY PASSWORD = 'CsE344RaNdOm$Key';

CREATE DATABASE SCOPED
CREDENTIAL QueryCredential
WITH IDENTITY = 'reader2020', SECRET = '20wiUWcse()';


CREATE EXTERNAL DATA SOURCE CSE344_EXTERNAL
WITH
( TYPE = RDBMS,
  LOCATION='khangishandsome.database.windows.net',
  DATABASE_NAME = 'cse344_readonly',
  CREDENTIAL = QueryCredential
);

DROP EXTERNAL TABLE  Flights;
DROP EXTERNAL TABLE  Carriers;
DROP EXTERNAL TABLE  Weekdays;
DROP EXTERNAL TABLE  Months;
DROP  TABLE  userInfo;
DROP  TABLE  reservations;
DROP  TABLE  ID;
DROP  TABLE  capacity;

CREATE EXTERNAL TABLE Flights(
  fid int,
  month_id int,
  day_of_month int,
  day_of_week_id int,
  carrier_id varchar(7),
  flight_num int,
  origin_city varchar(34),
  origin_state varchar(47),
  dest_city varchar(34),
  dest_state varchar(46),
  departure_delay int,
  taxi_out int,
  arrival_delay int,
  canceled int,
  actual_time int,
  distance int,
  capacity int,
  price int
) WITH (DATA_SOURCE = CSE344_EXTERNAL);

CREATE EXTERNAL TABLE Carriers(
  cid varchar(7),
  name varchar(83)
) WITH (DATA_SOURCE = CSE344_EXTERNAL);

CREATE EXTERNAL TABLE Weekdays(
  did int,
  day_of_week varchar(9)
) WITH (DATA_SOURCE = CSE344_EXTERNAL);

CREATE EXTERNAL TABLE Months
(
  mid int,
  month varchar(9)
) WITH (DATA_SOURCE = CSE344_EXTERNAL);

SELECT COUNT(*) FROM Flights;


*/

CREATE TABLE reservations (
    rid INT,  
    username varchar(20),
    fid INT, 
    day INT,
    carrier_id VARCHAR(7),
    flight_num INT,
    origin VARCHAR(40),
    dest VARCHAR(40),
    duration INT,
    capacity INT,
    price INT, 
    paid INT, 
    cancelled INT, 
    direct INT 
);



CREATE TABLE userInfo (
    username VARCHAR(20),
    hashedPassword VARBINARY(20),
    salt VARBINARY(16),
    balance INT, 
);

CREATE TABLE ID (
    ID INT
);

CREATE TABLE capacity (
    fid int,
    numberOfbooked int
);    
    