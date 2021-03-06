PGObserver
==========

Tool to monitor PostgreSQL performance metrics including:

 * Stored procedure data
 * Table IO statistics
 * CPU load

Data is gathered by a single Java application. Gather intervals for the different metrics can be set on a per host per metric basis.

The web frontend is a Python CherryPy app. See the screenshot folder for examples.

A testdata.py script is included to generate minimalistic testdata.

Visit us at http://tech.zalando.org

Setup
-----

 * Create schema from sql/schema.sql where you want to store data
 * Copy pgobserver.conf to home folder ~/.pgobserver.conf
 * Configure .pgobserver.conf to match your system setup
 * Create an unprivileged PostgreSQL user on the database you want to monitor
 * Add entry to monitor_data.hosts table to include the databases you want to monitor
 * Build a single jar including dependencies using mvn clean verify assembly:single
 * Start monitoring with: java -jar target/PGObserver-Gatherer-1.0-SNAPSHOT-jar-with-dependencies.jar
 * For CPU load created the stored procedure from sql/cpuload.sql , this is a plpythonu function

License
-------

Copyright 2012 Zalando GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
