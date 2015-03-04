/*
* William Prata
* Ashley Navin
* V 2.20 
*/
import java.util.*;
public class MyOperatingSystem implements OperatingSystem{
    
boolean loadComplete = false;
Hardware hw;  //my hardware of type Hardware
int progLength [] = new int[Hardware.Disk.blockSize]; //used as a temp storage
int progStart  [] = new int[Hardware.Disk.blockSize]; //used to store the start addres of each program
Queue<Integer> sched = new LinkedList<Integer>(); // used as the schedule queue
int currentProgram; // used for scheduler
int totalBlocks = 0; //used as a total block count
int blockCount=1; //temp variable to use as a pointer starting at one

    public MyOperatingSystem(Hardware hw) //constructor 
    {
        this.hw=hw;
    } 
   
    //method to populate an array that will hold all of the block sizes of the programs on the disks
   //method also fills an array that holds the start address of all of the programs on the disk 
    public void fillTempIndex()  
   {
       totalBlocks = 0;
       for(int i=0; i<Hardware.Disk.blockSize; i++)
       {
           
           this.progLength[i]= hw.fetch(Hardware.Address.userBase + i); //populate tempIndex array index i with the data that is in the index block at userBase+i address
           totalBlocks += this.progLength[i]; 
           if(i == 0) {
        	   progStart[0] = Hardware.Address.userBase + 32;
           } else {
        	   progStart[i] = progStart[i-1] + (this.progLength[i-1] * 32);
           }
       }
   }//end method
    
    //non preemptive schedular method to add programs to it
    public void startSched()  
    {
        for(int i = 0; i<Hardware.Disk.blockCount; i++)
        {
           if(hw.fetch(Hardware.Address.userBase + i) != 0)
            {
                sched.add(i);

            }//end if  
           break; //else all programs are in
        } // end for   
    }//end method

    // method to get program number
    public int getProgram()
    {
       int programNum = sched.remove();
       return programNum;
    }//end method
   
    //method to traverse scheduler to find program number needed for exec system call
 /*
    public int getID(int x)
    {
        //int ID;
        //return ID;
    }
*/
    @Override
    public void interrupt(Hardware.Interrupt it) {      
  	 MyOperatingSystem.this.fillTempIndex(); // populate the array of disk index with the first disk block
         this.startSched(); // add to scheduler
         if(it == Hardware.Interrupt.disk)    //Disk Interrupt Handler
        {
       	   	if(hw.fetch(Hardware.Address.userBase) != 0) //if the disk is not empty (something is in word 0 block 0 of primary storage)
            {
            	if(loadComplete == false) {  //check the boolean to see if load has been completed
                	if(blockCount <= totalBlocks) { //check the temp index and use as a loop to load blocks to primary storage
                		
//System.out.println("loading:" + blockCount + " of total:" + totalBlocks);
                        hw.store(Hardware.Address.diskBlockRegister, blockCount); // sets the first thing to be read disk block 0
                        hw.store(Hardware.Address.diskAddressRegister, Hardware.Address.userBase + (Hardware.Disk.blockSize*blockCount)); //set the disk address to start at the userbase
                        hw.store(Hardware.Address.diskCommandRegister, Hardware.Disk.readCommand); // command to start reading the disk                                     
                	} else {
                    	loadComplete = true; //sets load to true so it will not load again
                        currentProgram = this.getProgram();  //get program number from the scheduler                   
//System.out.println("Load complete, run prog " + currentProgram);
                        hw.store(Hardware.Address.baseRegister, Hardware.Address.userBase+32);//loads the running process start to the first run address
                    	hw.store(Hardware.Address.topRegister, (Hardware.Address.userBase+32 + (this.progLength[currentProgram]*32))); //loads the running process end to the last run address
                        hw.store(Hardware.Address.PCRegister, Hardware.Address.userBase+32); // commands the start of the processing to the start of the program
                    }                   
              		 blockCount++; // increment the block count
            	}
            }else{
                hw.store(Hardware.Address.haltRegister, 1); // if disk is empty halt the system
            }
        }else if(it == Hardware.Interrupt.countdown) //countdown interrupt
        {
//System.out.println("countdown");
        }else if(it == Hardware.Interrupt.illegalInstruction) //illegal instruction interrupt
        {
             hw.store(Hardware.Address.haltRegister, 1);// halts the system if there is an illegal instruction requested
//System.out.println("illegalInstruction");
        }else if(it == Hardware.Interrupt.invalidAddress) //invalid address interrupt
        {
             hw.store(Hardware.Address.haltRegister, 1); //halts the system if there is an invalid Address request
//System.out.println("invalidAddress");
        }else if(it == Hardware.Interrupt.reboot)  //on startup
        { 
//System.out.println("reboot");
            //runs the idle process 
            hw.store(Hardware.Address.baseRegister, Hardware.Address.idleStart);//loads the running process start to the first run address
            hw.store(Hardware.Address.topRegister, Hardware.Address.idleEnd); //loads the running process end to the last run address
            hw.store(Hardware.Address.PCRegister, Hardware.Address.idleStart); // commands the start of the processing to the start of the program
            
            hw.store(Hardware.Address.diskBlockRegister, 0); // sets the first thing to be read disk block 0
            hw.store(Hardware.Address.diskAddressRegister, Hardware.Address.userBase); //set the disk address to start at the userbase
            hw.store(Hardware.Address.diskCommandRegister, Hardware.Disk.readCommand); // command to start reading the disk
        }else if(it == Hardware.Interrupt.systemCall)
        {
            int status; // will hold the Hardware status given after a system call
            int systemId = hw.fetch(Hardware.Address.systemBase);  //type of system call being called    	

            //switch statement to handle different types of systemcalls
            switch(systemId) {
                    case OperatingSystem.SystemCall.exit: //exit system call 
                            hw.store(Hardware.Address.haltRegister, 1); //halt when a systemCall is called
                            break;
                    case OperatingSystem.SystemCall.exec: //execute system call
                            int programId = hw.fetch(Hardware.Address.systemBase+1);//id of the program being called
                            currentProgram = sched.remove();
                            if(programId > 32) { //make sure it is a legal addressable program id
                                    this.interrupt(Hardware.Interrupt.invalidAddress); //only 32 programs max possible on a disk
                            } else { //program Id is legal
                                int programStart = progStart[programId]; // set the programStart to the first address of the id program from the progStart array
                                int programEnd = programStart + (32 * this.progLength[programId]); //get programEnd based on the progStart array

//System.out.println("Exec:" + programId + " " + programStart +" " + programEnd);
                                hw.store(Hardware.Address.baseRegister, programStart);//loads the running process start to the first run address
                                hw.store(Hardware.Address.topRegister, programEnd); //loads the running process end to the last run address
                                hw.store(Hardware.Address.PCRegister, programStart); // commands the start of the processing to the start of the program

                                status = hw.fetch(Hardware.Address.systemBase); // get the status to the status address
                                int rtnProgramId = hw.fetch(Hardware.Address.systemBase+1); //return the new program id
//System.out.println("Return:" + status + " ProgId:" + rtnProgramId);
                            if(status != Hardware.Status.ok) {
                            }
                                }
                                break;
                    case OperatingSystem.SystemCall.yield: //yield system call
                            hw.store(Hardware.Address.systemBase, Hardware.Status.ok);  //hardware status is ok  	
                            break;
                    case OperatingSystem.SystemCall.putSlot: //put slot system call
                            int putslot = hw.fetch(Hardware.Address.systemBase+1); // get the slot information from the first program
                            int data = hw.fetch(Hardware.Address.systemBase+2); //put the infromation 
                            hw.store(Hardware.Address.systemBase, Hardware.Status.ok); //hardware status will be ok
//System.out.println("put");
                            break;
                    case OperatingSystem.SystemCall.getSlot:  //get slot system call
                            int getslot = hw.fetch(Hardware.Address.systemBase); //get slot information
                            status = hw.fetch(Hardware.Address.systemBase); // get status infromation
                            if(status == Hardware.Status.ok) {  //if the status returned is ok
                                int slotValue = hw.fetch(Hardware.Address.systemBase+1); //get the slot data
                                int senderId = hw.fetch(Hardware.Address.systemBase+2); //get the location data
//System.out.println("get:" + slotValue + " " + senderId);
                            }
                            break;        			
        	}//end switch            
        }//end if else    
    } // end method    
}//end class