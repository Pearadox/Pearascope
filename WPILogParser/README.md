OPEN THIS FOLDER DIRECTLY IN VS CODE - DO NOT OPEN THE PARENT FOLDER

Process Pearadox 5414 wpilog to generate an Excel file with specific log entries of interest and perform automated analysis.

This project should build with 'gradle build' and run either with 'gradle run' or via java commandline (java -jar WPILogParser.java). If no commandline parameters, it will process all files in the ./output/ directory.  If the first commandline parameter is "-raw", it will also generate a separate raw dump of each log.  All other parameters, if provided, should be full paths (absolute or relative) to log files to process - multiple params for multiple logs can be provided.

There's something awry with running/debugging this project from within Visual Studio Code - some issue with references that I couldn't figure out after hours of troubleshooting.  So debugging may require multiple gradle build and run cycles, using System.out, etc.

Current class used for main is PrintLogSimplified.  All other classes here were earlier attempts to prove the concept, each holding some reference code that may still be of value.  PrintLogWPI.java is directly from current WPILib examples. WPILogParser.java was an early and simple proof-of-concept of being able to read a log using WPILib classes.  PrintLog.java shows processing of finish control and metadata control records, neither of which seem to appear in our logs, hence the "Simplified" class successor.