generate.TestData <- function(
  numberOfPatients,
  outputfilename,
  visitNames = c(""),
  observations = c(""),
  ...
  )
{
  
  #Get the frame of subject IDs.
  finalData <- data.frame(seq(1,numberOfPatients))
  
  #Set the column names of the data frame.
  colnames(finalData) <- c("SUBJECT_ID")
  
  #If we have visit names, add a column for each.
  if(length(visitNames) > 1 && visitNames[1] != "")
  {
    #Create a frame of all the visitnames.
    visitFrame <- data.frame(visitNames)
    
    visitFrame <- merge(finalData,visitFrame,all=TRUE)
    
    colnames(visitFrame) <- c("SUBJECT_ID", "VISIT")
  }
  
  if(length(observations) > 1 && observations[1] != "")
  {
    #Create a frame of all the observations.
    observationsFrame <- data.frame(observations)
    
    #Cross Join the final data frame with the visits and observations.
    observationsFrame <- merge(visitFrame,observationsFrame,all=TRUE)
    
    #Set the new column headers.
    colnames(observationsFrame) <- c("SUBJECT_ID", "VISIT", "OBSERVATION")
    
  }
  
  #For each of the additional parameters we add a column.
  remainingParameters <- list(...)
  nParameters <- length(remainingParameters)

  #Loop through each of the additional parameters.
  for(i in seq(1,nParameters,by=3)) {
    
    #Each additional parameter should come in threes.
    #The first item is the column name.
    columnName <- remainingParameters[[i]]
    
    #The second item is the type of column.
    columnType <- remainingParameters[[i+1]]
    
    #The third item is a list of the possible values.
    columnPossible <- remainingParameters[[i+2]]
    
    if(columnType == "NUMERIC_RNORM")
    {
      
      #Split the min and max values out.
      minNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][1])
      maxNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][2])
      
      #Get the mean of the min/max.
      meanNumber <- mean(c(minNumber,maxNumber))
      
      #Create the random numbers.
      finalData[columnName] <- abs(rnorm(numberOfPatients, mean=meanNumber, sd=4))
    }
    else if(columnType == "NUMERIC_RNORM_VISIT")
    {
      
      #This is the min of our initial range.
      minNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][1])
      
      #This is the max of our initial range.
      maxNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][2])
      
      #This is the visit name.
      visitName <- strsplit(columnPossible, ":")[[1]][3]
      
      #Get the mean of the min/max.
      meanNumber <- mean(c(minNumber,maxNumber))
      
      #Create the random numbers.
      visitFrame[which(visitFrame$VISIT == visitName), columnName] <- abs(rnorm(numberOfPatients, mean=meanNumber, sd=4))
    }
    else if(columnType == "NUMERIC_RNORM_VISIT_OBS")
    {
      
      #This is the min of our initial range.
      minNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][1])
      
      #This is the max of our initial range.
      maxNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][2])
      
      #This is the visit name.
      visitName <- strsplit(columnPossible, ":")[[1]][3]
      
      #This is the observation name.
      observationName <- strsplit(columnPossible, ":")[[1]][4]
      
      #Get the mean of the min/max.
      meanNumber <- mean(c(minNumber,maxNumber))

      #Create the random numbers.
      observationsFrame[observationsFrame$VISIT == visitName & observationsFrame$OBSERVATION == observationName, columnName] <- abs(rnorm(numberOfPatients, mean=meanNumber, sd=4))
    }    
    else if(columnType == "NUMERIC_RNORM_VISIT_OBS")
    {
      
      #This is the min of our initial range.
      minNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][1])
      
      #This is the max of our initial range.
      maxNumber <- as.numeric(strsplit(columnPossible, ":")[[1]][2])
      
      #This is the visit name.
      visitName <- strsplit(columnPossible, ":")[[1]][3]
      
      #This is the observation name.
      observationName <- strsplit(columnPossible, ":")[[1]][4]
      
      #Get the mean of the min/max.
      meanNumber <- mean(c(minNumber,maxNumber))
      
      #Create the random numbers.
      visitFrame[visitFrame$VISIT == visitName & visitFrame$OBSERVATION == observationName, columnName] <- abs(rnorm(numberOfPatients, mean=meanNumber, sd=4))
    }
    else if(columnType == "CATEGORICAL_VISIT")
    {
      
      #Split the categories into a list.
      valuesPossible <- strsplit(columnPossible, ":")[[1]][1]
      valuesPossible <- strsplit(valuesPossible,";")
      
      #This is the visit name.
      visitName <- strsplit(columnPossible, ":")[[1]][2]     

      #Assigned random variables based on the list.
      visitFrame[visitFrame$VISIT == visitName, columnName] <- sample(valuesPossible[[1]], numberOfPatients, replace=TRUE) 
    }
    else
    {
      #Create a random distribution from items in the list.
      finalData[columnName] <- sample(columnPossible, numberOfPatients, replace=TRUE)      
    }


  }
  
  #Randomly delete some records to simulate missing visits.
  #finalData <- finalData[sample(nrow(finalData), nrow(finalData) * .9), ]
  
  #If we are doing a visit file too, remove some records from there.
  if(length(visitNames) > 1 && visitNames[1] != "")
  {
    visitFrame <- visitFrame[sample(nrow(visitFrame), nrow(visitFrame) * .9), ]
  }
  
  #We need MASS to dump the matrix to a file.
  require(MASS)
  
  #Write the final data file.
  write.matrix(finalData,outputfilename,sep = "\t")
  
  #If we are doing a visit file too, write that here.
  if(length(visitNames) > 1 && visitNames[1] != "")
  {
    write.matrix(visitFrame,paste(outputfilename,".visits",sep=""),sep = "\t")
  }  
  
  #If we are doing a visit file too, write that here.
  if(length(observations) > 1 && observations[1] != "")
  {
    write.matrix(observationsFrame,paste(outputfilename,".visits.observations",sep=""),sep = "\t")
  }    
  
}