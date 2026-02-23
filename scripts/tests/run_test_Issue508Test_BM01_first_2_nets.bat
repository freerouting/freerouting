pushd "%~dp0..\.."
if not exist logs mkdir logs
call gradlew.bat test --tests "app.freerouting.tests.Issue508Test_BM01_first_2_nets" > "logs\freerouting-Issue508Test_BM01_first_2_nets.log" 2>&1
popd

