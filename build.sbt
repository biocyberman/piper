
// ********************************************
// The basics
// ********************************************
name := "Piper"

organization := "molmed"

version := "v1.3.0"

scalaVersion := "2.10.1"

scalaVersion := "2.10.4"
// ********************************************
// Tests
// ********************************************
Seq(testNGSettings:_*)

testNGSuites := Seq("src/test/resources/testng.xml")
