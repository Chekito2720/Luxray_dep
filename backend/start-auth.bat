@echo off
start /B "auth-service" java -jar "C:\DDI\luxray\backend\auth-service\target\auth-service-1.0.0-SNAPSHOT.jar" > "C:\DDI\luxray\backend\logs\auth-batch.out" 2> "C:\DDI\luxray\backend\logs\auth-batch.err"
