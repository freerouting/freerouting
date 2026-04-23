pushd "%~dp0..\.."
if not exist logs mkdir logs
call gradlew.bat test --tests "app.freerouting.tests.Dac2020Bm01RoutingTest" > "logs\freerouting-Dac2020Bm01RoutingTest.log" 2>&1
popd
