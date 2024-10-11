Please, add a link to the Jira and make sure your fix is well described.

Also, we do not have CI enabled in gitlab yet. For that reason, make sure you run the full testsuite to verify if nothing is broken:
mvn clean package  -Pproxy -Dmaven.test.failure.ignore=true -DfailIfNoTests=false -fae > output.txt
mvn clean package  -Pproxy -Dmaven.test.failure.ignore=true -DfailIfNoTests=false -fae -Dtest.ipv6=true > outputIpv6.txt

After running, make sure that no "Failure"s show up in the output files.