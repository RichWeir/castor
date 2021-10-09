package coe_cellular_automata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.sql.*;
import org.sqlite.*;

public class CellularAutomata {
	//ArrayList<Cell> cellList = new ArrayList<Cell>();
	ArrayList<Cells> cellsList = new ArrayList<Cells>();
	ArrayList<Integer> cellsListChangeState = new ArrayList<Integer>();
	
	//ArrayList<Cell> cellListMaximum = new ArrayList<Cell>();
	int numIter=15000;
	float harvestMax,harvestMin, objValue, maxObjValue;
	double globalWeight =0.0; // needs be of high precision
	boolean finalPlan = false;
	boolean globalConstraintsAchieved = false;
	int lateSeralTarget;
	
	Grid landscape = new Grid();//Instantiate the GRID
	//LandCoverConstraint beo = new LandCoverConstraint();
	float[] planHarvestVolume = new float[landscape.numTimePeriods];
	float[] planGSVolume = new float[landscape.numTimePeriods];
	int[] planLateSeral = new int[landscape.numTimePeriods];
	float[] maxPlanHarvestVolume = new float[landscape.numTimePeriods];
	int[] maxPlanLateSeral = new int[landscape.numTimePeriods];
	ArrayList<ArrayList<LinkedHashMap<String, Double>>> yields = new ArrayList<ArrayList<LinkedHashMap<String, Double>>>();
	ArrayList<HashMap<String, float[]>> yieldTable = new ArrayList<HashMap<String, float[]>>();
	public ArrayList<ForestType> forestTypeList = new ArrayList<ForestType>() ;
	public ArrayList<LandCoverConstraint> landCoverConstraintList = new ArrayList<LandCoverConstraint>() ;
	//Variables for simulate2
	float maxCutLocal = 0L;
	float[] maxCutGlobal = new float[landscape.numTimePeriods];
	/** 
	* Class constructor.
	*/
	public CellularAutomata() {
	}
	
	/** 
	* Simulates the cellular automata. This is the main algorithm for searching the decision space following Heinonen and Pukkala 
	* 1. Local level decisions are optimized by
	* 	a. create a vector of randomly sampled without replacement cell indexes 
	* 	b. pull a random variable and determine if the cell is to be mutated. If mutated pick random state
	* 	c. pull a random variable and determine if the cell is to be innovated. If innovated pick best state
	* 	e. go to next cell
	* 	f. stop when number of iterations reached.
	* 2. Global level decisions are optimized
	* 	a. using the local level as the starting point. Global weight (b) =0;
	* 	b. estimate local + global objective function
	* 	c. for each cell evaluate the best state
	* 	d. increment the global weight b ++ 0.1 and go to the next iteration
	* 	e. stop when global penalties are met
	*/
	public void simulate2() {
		boolean mutate = false;
		boolean innovate = true;
		int counterLocalMaxState = 0;
		int currentMaxState;
		Random r = new Random(15); // needed for mutation or innovation probabilities? 
		harvestMin = 19000000;
		setLandCoverConstraintRecruitment(); //In cases no-harvesting decision does not meet the constraint -- remove harvesting during these periods and as a penalty -- thus ahciving the paritial amount
		setMaxCutLocal();//scope all of the cells to determine the maxHarvVol	
		randomizeStates(r);
		
		System.out.println("Starting local optimization..");
		//local optimization
		for(int i = 0; i < 100; i ++) {
			//Local level optimization
			System.out.println("Local optimization iter:" + i);
			Collections.shuffle(cellsListChangeState); // randomize which cell gets selected.
			
			for(int j = 0; j < cellsListChangeState.size(); j++) {
				if(mutate) {
					cellsList.get(cellsListChangeState.get(j)).state = r.nextInt(forestTypeList.get(cellsList.get(cellsListChangeState.get(j)).foresttype).stateTypes.size()); //get a random state
				}
				if(innovate) {
					currentMaxState = getMaxStateLocal(cellsListChangeState.get(j));
					if(cellsList.get(cellsListChangeState.get(j)).state == currentMaxState) {
						counterLocalMaxState ++;
						continue;
					}else {
						cellsList.get(cellsListChangeState.get(j)).state = currentMaxState; //set the maximum state with no global constraints
					}
				}
			}	
			
			if(counterLocalMaxState == cellsListChangeState.size()) {
				break;
			}else {
				counterLocalMaxState = 0;
			}	
		}
		//Set the global level objectives
		for(int c =0; c < cellsListChangeState.size(); c++) {// Iterate through each of the cell and their corresponding state
			planHarvestVolume = sumVector(planHarvestVolume,  forestTypeList.get(cellsList.get(cellsListChangeState.get(c)).foresttype).stateTypes.get(cellsList.get(cellsListChangeState.get(c)).state).get("harvVol")) ;//harvest volume
		}
		
		for(int gs =0; gs < cellsList.size(); gs++) {
			planGSVolume = sumVector(planGSVolume,  forestTypeList.get(cellsList.get(gs).foresttype).stateTypes.get(cellsList.get(gs).state).get("gsVol")) ;
		}
		
		System.out.println("Starting global optimization..");
		int cell;
		//global optimization
		for(int g =0; g < 10000; g++) {	
			if(globalConstraintsAchieved) {
				break;
			}
			
			Collections.shuffle(cellsListChangeState); // randomize which cell gets selected.
			for(int j = 0; j < cellsListChangeState.size(); j++) {
				if(globalConstraintsAchieved) {
					break;
				}
				cell = cellsListChangeState.get(j);				
				cellsList.get(cell).state = getMaxStateGlobal(cell); //set the maximum state with global constraints			
			}
			
			System.out.print("iter:" + g + " global weight:" + globalWeight );
			globalWeight = globalWeight + 0.01; //increment the global weight
			for(int k =0; k < planHarvestVolume.length; k++){
				System.out.print(" Vol:" + planHarvestVolume[k] + "  ");
			}
			System.out.println("");
		}
		 
		System.out.println("Solved");
		for(int f =0; f < planHarvestVolume.length; f++){
			System.out.print(" GS:" + planGSVolume[f] + "  ");
		}
		
		try {
			System.out.print("Saving states");
			saveResults();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private void setLandCoverConstraintRecruitment() {
		String variable;
		float[] value = null;
		boolean addToStateChange;
		int counter = 0;
		//TODO: loop through each constraint with a list of cell ids rather than each cell
		for(Cells c : cellsList) {
			
			addToStateChange = true;
			
			for(Integer constraint : c.landCoverList) {
				
				variable = landCoverConstraintList.get(constraint).variable;				
				value =  forestTypeList.get(c.foresttype).stateTypes.get(0).get(variable);
				
				switch(landCoverConstraintList.get(constraint).type) {
					case "ge": //greater or equal to
						for(int v =0; v < value.length; v ++) {
							if(  value[v] >=  landCoverConstraintList.get(constraint).threshold) {
								landCoverConstraintList.get(constraint).achievedConstraint[v] += 1f;
							}
						}
						break;
					case "le": //lesser or equal to
						for(int v =0; v < value.length; v ++) {
							if(value[v] <=  landCoverConstraintList.get(constraint).threshold) {
								landCoverConstraintList.get(constraint).achievedConstraint[v] += 1f;
							}
						
						}
						break;
					case "nh": // no harvest
						addToStateChange = false; //remove from the cellsListChangeState object
						break;
					}
															
			}
			
			if(c.manage_type > 0 && addToStateChange && forestTypeList.get(c.foresttype).stateTypes.size() > 1) {
				cellsListChangeState.add(counter);
			}
			
			counter ++;
		}
		
		System.out.println("");
		landscape.setLandscapeWeight((double) 1/cellsListChangeState.size());
	}

	public void saveResults ()  throws SQLException {
		try { //Get the data from the db
			Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/klochhea/clus/R/SpaDES-modules/forestryCLUS/Quesnel_TSA_clusdb.sqlite");		
			if (conn != null) {
				Statement statement = conn.createStatement();
				
				String dropResultsTable = "DROP TABLE IF EXISTS ca_result;";
				statement.execute(dropResultsTable);
				
				String makeResultsTable = "CREATE TABLE IF NOT EXISTS ca_result (pixelid integer, t1 numeric, t2 numeric, t3 numeric, t4 numeric, t5 numeric, t6 numeric, t7 numeric, t8 numeric, t9 numeric, t10 numeric);";
				statement.execute(makeResultsTable);

				statement.close();
				
				String insertResults =
					      "INSERT INTO ca_result (pixelid, t1,t2,t3,t4,t5,t6,t7,t8,t9,t10) VALUES (?,?,?,?,?,?,?,?,?,?,?);";
				
				float [] age;
				
				conn.setAutoCommit(false);
				PreparedStatement pstmt = conn.prepareStatement(insertResults);
				try {
					for(int c = 0; c < cellsList.size(); c++) {
						pstmt.setInt(1, cellsList.get(c).pixelid);
						age =  forestTypeList.get(cellsList.get(c).foresttype).stateTypes.get(cellsList.get(c).state).get("age");
			        	for(int t = 0; t < age.length; t++) {
			        		pstmt.setInt(t+2, (int) age[t]);
			         	}
			        	pstmt.executeUpdate();		        	
					}
				}finally {
					System.out.println("...done");
					//pstmt.executeBatch();
					pstmt.close();
					conn.commit();
					conn.close();
				}     
			}
		}catch (SQLException e) {
	            System.out.println(e.getMessage());
	        }
	}
	
	private void randomizeStates(Random r) {
		for(int c = 0 ; c < cellsListChangeState.size(); c++) {
			cellsList.get(cellsListChangeState.get(c)).state = r.nextInt(forestTypeList.get(cellsList.get(cellsListChangeState.get(c)).foresttype).stateTypes.size());			
		}
	}

	/**
	* Finds the quantity across all cells and schedules of the maximum amount of volume harvested 
	* across all planning horizons (maxCutLocal).
	*/
	private void setMaxCutLocal() {
		float tempCut = 0 ;	
		for(int c =0; c < cellsListChangeState.size(); c++) {
			for(int s= 0; s < forestTypeList.get(cellsList.get(cellsListChangeState.get(c)).foresttype).stateTypes.size(); s++ ) {				
				
				tempCut = tempCut + sumFloatVector(forestTypeList.get(cellsList.get(cellsListChangeState.get(c)).foresttype).stateTypes.get(s).get("harvVol")) ;	
				if(maxCutLocal < tempCut) {
					maxCutLocal =   tempCut;
				}		
				tempCut = 0L;
			}		
		}
		//Note that the max og local is 1 for all periods. 
	}
	
	private float sumFloatVector(float[] object) {
		float out = 0;
		for(int f = 0; f < object.length; f ++) {
			out = out + object[f];
		}
		return out;
	}

	/**
	* Retrieves the max state at the local scale. The rank of alternative schedules is based on Heinonen and Pukkala:
	* Ujk = SUM(wi*ui(qi)) where wi is the weight for objective i, 
	* ui is the priority function for objective i and qi is the quantity of the objective i 
	* 
	* @param i the cell index
	* @return the index of the cell state which maximizes the objectives
	*/
	private int getMaxStateLocal(int i) {
		double maxValue = 0.0, stateValue = 0.0;
		double isf =0.0;
		int stateMax = 0;
		float[] harvVol, age;
		float[][] propN = getNeighborProportion(cellsList.get(i).adjCellsList);
		float maxPropNH = sumPropN(propN[0]);
		float maxPropNAge = sumPropN(propN[1]);
		
		for(int s = 0; s < forestTypeList.get(cellsList.get(i).foresttype).stateTypes.size(); s++) {
				
			harvVol = forestTypeList.get(cellsList.get(i).foresttype).stateTypes.get(s).get("harvVol");
			isf = sumArray(harvVol)/maxCutLocal;
	
			age = forestTypeList.get(cellsList.get(i).foresttype).stateTypes.get(s).get("age");
		    stateValue = 0.3*isf + 0.65*getPropNHRank(harvVol, propN[0],maxPropNH)+ 0.05*getPropNAgeRank(age, propN[1],maxPropNAge);
				
			if(maxValue < stateValue) {
				maxValue = stateValue;
				stateMax = s;
			}
				
		}
		return stateMax;
	}

	

	private float getPropNHRank(float[] attribute, float[] propN, float maxPropN) {
		if(maxPropN == 0f) {
			return 0f;
		}else {
			float rank =0;
			for(int r =0; r < attribute.length; r++) {
				if(attribute[r] > 0) {
					rank = rank + propN[r]; // this equates to one times the propNH
				}
			}
			return rank/maxPropN;
		}
	}
	
	private float getPropNAgeRank(float[] attribute, float[] propN, float maxPropN) {
		if(maxPropN == 0f) {
			return 0f;
		}else {
			float rank =0;
			for(int r =0; r < attribute.length; r++) {
				if(attribute[r] > 100) {
					rank = rank + propN[r]; // this equates to one times the propNH
				}
			}
			return rank/maxPropN;
		}
	}

	private float sumPropN(float[] propN) {
		float out =0;
		for(int n = 0; n < propN.length; n++) {
			out = out + propN[n];
		}
		return out;
	}

	/**
	* Retrieves the max state when linking the local and global objectives. 
	* The rank of alternative schedules is based on Heinonen and Pukkala:
	*
	* Local level rank of alternative states
	* Ujk = SUM(wi*ui(qi)) where wi is the weight for objective i, 
	* ui is the priority function for objective i and qi is the quantity of the objective i 
	*
	* Global level rank of alternative states
	* P =SUM(vl*pl(gl)) where vl is the weight for global objective l, 
	* pl is the priority function for objective l and gl is the quantity of the objective l 
	* 
	* Combination rank or linkage between the two scales
	* Rjk = a/A*Ujk + b*P where Rjk is the rank of alternative states, a is the
	* area of the cell, A is the total area of all cells, b is the globalWeight to be incremented.
	* 
	* @param id the cell index
	* @return the index of the cell state which maximizes the objectives
	*/
	private int getMaxStateGlobal(int i) {
		double maxValue = 0.0, P = 0.0, U =0.0 , hfc = 0.0;
		double isf =0.0, lc =1.0;
		double stateValue;
		int stateMax = 0;
		float[] harvVol, age;
		float[][] propN = getNeighborProportion(cellsList.get(i).adjCellsList);
		float maxPropNH = sumPropN(propN[0]);
		float maxPropNAge = sumPropN(propN[1]);
		
		int oldState = cellsList.get(i).state;
		int foresttype = cellsList.get(i).foresttype;
		planHarvestVolume = subtractVector(planHarvestVolume, forestTypeList.get(foresttype).stateTypes.get(oldState).get("harvVol"));
		planGSVolume = subtractVector(planGSVolume, forestTypeList.get(foresttype).stateTypes.get(oldState).get("gsVol"));
		
		for(int s = 0; s < forestTypeList.get(foresttype).stateTypes.size(); s++) {
				
			harvVol = forestTypeList.get(foresttype).stateTypes.get(s).get("harvVol");
			isf = sumArray(harvVol)/maxCutLocal;
	
			age = forestTypeList.get(foresttype).stateTypes.get(s).get("age");
		    U = 0.3*isf + 0.65*getPropNHRank(harvVol, propN[0],maxPropNH)+ 0.05*getPropNAgeRank(age, propN[1],maxPropNAge);
				
			hfc = getHarvestFlowConstraint(harvVol, planHarvestVolume);
			
			P = 0.5*hfc + 0.5*lc; 
			stateValue = landscape.weight*U + globalWeight*P;
				
			if(maxValue < stateValue) {
				maxValue = stateValue;
				stateMax = s;
				if(P > 0.999) { //this is the threshold for stopping the simulation
					globalConstraintsAchieved = true;
					break;
				}
			}
				
		}
		
		planHarvestVolume = sumVector(planHarvestVolume, forestTypeList.get(foresttype).stateTypes.get(stateMax).get("harvVol"));
		planGSVolume = sumVector(planGSVolume, forestTypeList.get(foresttype).stateTypes.get(stateMax).get("gsVol"));
	
		return stateMax;
	}

	private double getHarvestFlowConstraint(float[] harvVol, float[] planHarvestVolume2) {
		double hvConstraint = 0.0;
		double volActual;
		double weight = (double) 1/harvVol.length; 
		for(int h = 0; h < harvVol.length; h++) {
			volActual = harvVol[h] + planHarvestVolume2[h];
			if( volActual >= harvestMin) {
				hvConstraint += weight;
			}else {
				hvConstraint += weight*(volActual/harvestMin);
			}
		}
		return hvConstraint;
	}

	/**
	 * Resets any objectives whose value is greater than one to a max of one
	 * @param objective an array of objective values
	 * @return an array whose elements have a max value of 1.0
	 */
	private float[] checkMaxContribution(float[] objective) {
		float out[] = new float[landscape.numTimePeriods];
		
		for(int t = 0; t < objective.length; t++) {
			if(objective[t] > 1L ) {
				out[t] = 1L ;
			}else {
				out[t] = objective[t];
			}
		}
		return out;
	}

	/** 
	* Simulates the cellular automata follow Mathey et al. This is the main algorithm for searching the decision space. 
	* 1. global level penalties are determined which incentivize cell level decisions
	* 2. create a vector of randomly sampled without replacement cell indexes 
	* 3. the first random cell is tested if its at maximum state which includes context independent values such as
	* the maximum amount of volume the cell can produce of the planning horizon and context dependent values such as
	* its contribution, as well as, the surrounding cells contribution to late-seral forest targets. 
	* 4. If already at max state then proceed to the next cell. Else update to its max state.
	* 5. If there are no more stands to change or the number of iterations has been reached - end.
	*/
	public void simulate() {
		int block = 0;
		int numIterSinceFreq = 0;
		int[] blockParams = {0, 2000, 4000,7000,10000, 1000000}; // add a large number so there's no out of bounds issues
		int[] freqParams = {0, 300,200,100,1,1};
		boolean timeToSetPenalties = false;
		int [] maxStates = new int[cellsList.size()];
		landscape.setPenaltiesBlank();//set penalty parameters - alpha, beta and gamma as zero filled arrays
		
		for(int i=0; i < numIter; i++) { // Iteration loop
					
			if(i >= blockParams[block+1]) { // Go to the next block
				block ++;
			}
			
			if(block > 0 && numIterSinceFreq >= freqParams[block]) { // Calculate at the freq level
				timeToSetPenalties = true;
				numIterSinceFreq = 0;
			}
				
			numIterSinceFreq ++;
			
			Arrays.fill(planHarvestVolume, 0L); //set the harvestVolume indicator
			Arrays.fill(planLateSeral, 0); //set the late-seral forest indicator
			objValue = 0; //reset the object value;
			
			int[] rand = new Random().ints(0, cellsList.size()).distinct().limit(cellsList.size()).toArray();; // Randomize the stand or cell list
			
			for(int j = 0; j < rand.length; j++) { //Stand or cell list loop
				int maxState = getMaxState(rand[j]);
				if(cellsList.get(rand[j]).state == maxState) { //When the cell is at its max state - go to the next cell
					//System.out.println("Cell:" + cellList.get(rand[j]).id + " already at max");
					if(j == cellsList.size()-1) {
						finalPlan = true;
					}
					continue; // Go to the next cell -- this one is already at its max
				}else{ // Change the state of the cell to its max state and then exit the stand or cell list loop
					//System.out.println("Cell:" + cellsList.get(rand[j]).pixelid + " change from " + cellList.get(rand[j]).state + " to " + maxState );
					cellsList.get(rand[j]).state = maxState; //transition function - set the new state to the max state
					break;
				}
				
			}
			
			//Output the global indicators (aggregate all cell level values)
			for(int c =0; c < cellsList.size(); c++) {// Iterate through each of the cell and their corresponding state
				int state = cellsList.get(c).state;
				double isf, dsf;
					
				//isf = DoubleStream.of(multiplyVector(cellList.get(c).statesPrHV.get(state), sumVector(landscape.lambda, subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
				//dsf = DoubleStream.of(multiplyVector(divideScalar(sumVector(cellList.get(c).statesOG.get(state), getNeighborLateSeral(cellList.get(c).adjCellsList)), landscape.numTimePeriods*2), sumVector(landscape.lambda,landscape.gamma))).sum();
				//isf = DoubleStream.of(multiplyVector(landscape.lambda,multiplyVector(cellsList.get(c).statesPrHV.get(state), subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
				//dsf = DoubleStream.of(multiplyVector(landscape.oneMinusLambda, multiplyVector(divideScalar(sumIntDoubleVector(cellList.get(c).statesOG.get(state), getNeighborLateSeral(cellList.get(c).adjCellsList)), landscape.numTimePeriods*2), landscape.gamma))).sum();
					
				//objValue += isf + dsf; //objective value
				
				//planHarvestVolume = sumVector(planHarvestVolume, cellsList.get(c).statesHarvest.get(state)) ;//harvest volume
				//planLateSeral = sumIntVector(planLateSeral, cellsList.get(c).statesOG.get(state));
			}
			System.out.println("iter:"+ i + " obj:" + objValue);
			if(maxObjValue < objValue && i > 14000) {
				maxObjValue = objValue;
				maxPlanHarvestVolume = planHarvestVolume.clone();
				maxPlanLateSeral = planLateSeral.clone();
				
				for(int h =0; h < cellsList.size(); h++) {
					maxStates[h] = cellsList.get(h).state;
				}
			}
			//Set the global-level penalties
			if(timeToSetPenalties) {
				double[] alpha = getAlphaPenalty(planHarvestVolume, harvestMin);
				double[] beta = getBetaPenalty(planHarvestVolume, harvestMax);
				double[] gamma = getGammaPenalty(planLateSeral, lateSeralTarget);
				landscape.setPenalties(alpha, beta, gamma);
				timeToSetPenalties = false;
			}
			
			if(finalPlan || i == numIter-1) {
				System.out.println("All cells at max state in iteration:" + i);
				System.out.println("maxObjValue:" + maxObjValue);
				for(int t= 0; t < planHarvestVolume.length; t++) {
					System.out.print("HV @ " + t + ": " + planHarvestVolume[t]+", ");
					System.out.println();
					System.out.print("HV @ " + t + ": " + maxPlanHarvestVolume[t]+", ");
					System.out.println();
					System.out.print("LS @ " + t + ": " + planLateSeral[t]+", ");
					System.out.println();
					System.out.print("LS @ " + t + ": " + maxPlanLateSeral[t]+", ");
					System.out.println();
				}
				System.out.println();
				//print the grid at each time
				/*for(int g= 0; g < landscape.numTimePeriods; g++) {
				   System.out.println("Time Period:" + (g + 1));
				   int rowCounter = 0;
			        for (int l= 0; l < cellList.size(); l++){
			            if (cellList.get(l).statesOG.get(cellList.get(l).state)[g] == 0.0) {
			            	System.out.print(".");
			            }else {
			            	System.out.print("*");
			            }
			            rowCounter ++;
			            if(rowCounter == landscape.colSizeLattice) {
			            	 System.out.println();
			            	 rowCounter = 0;
			            }
			         }
			        
			        System.out.println();
			        System.out.println();
			        System.out.println(maxObjValue);
				}*/
				break;
			}
		}
		
		//Report final plan indicators
	}
	
	/**
	* Retrieves the penalty for late-seral forest
	* @param planLateSeral2	the plan harvest volume
	* @param lateSeralTarget2	the minimum amount of late-seral needed
	* @return 		an array of gamma penalties
	*/
	 private double[] getGammaPenalty(int[] planLateSeral2, float lateSeralTarget2) {
			double[] gamma = new double[landscape.numTimePeriods];
			for(int a = 0; a < planLateSeral2.length; a++ ) {
				if(planLateSeral2[a] <= lateSeralTarget2) {
					if(planLateSeral2[a] == 0.0) {//check divisible by zero
						gamma[a] = lateSeralTarget2/0.00001; //use a small number in lieu of zero
					}else {
						gamma[a] = (double) lateSeralTarget2/planLateSeral2[a];
					}
				}else {
					gamma[a] = 0.0;
				}
				
			}
			return gamma;
	}
	 
	/**
	* Retrieves the penalty for over harvesting
	* @param planHarvestVolume2	the plan harvest volume
	* @param harvestMax2	the maximum harvest volume
	* @return 		an array of beta penalties
	*/
	private double[] getBetaPenalty(float[] planHarvestVolume2, float harvestMax2) {
			double[] beta = new double[landscape.numTimePeriods];
			for(int a = 0; a < planHarvestVolume2.length; a++ ) {
				if(planHarvestVolume2[a] >= harvestMax2) {
					beta[a] = planHarvestVolume2[a]/harvestMax2;
				}else {
					beta[a] = 0.0;
				}				
			}
			return beta;
	}
	
	/**
     * Retrieves the penalty for under harvesting
     * @param planHarvestVolume2	the plan harvest volume
     * @param harvestMin2	the minimum harvest volume
     * @return 		an array of alpha penalties
     */
	private double[] getAlphaPenalty(float[] planHarvestVolume2, float harvestMin2) {
		double[] alpha = new double[landscape.numTimePeriods];
		for(int a = 0; a < planHarvestVolume2.length; a++ ) {
			if(planHarvestVolume2[a] <= harvestMin2) {
				if(planHarvestVolume2[a] == 0.0) {//check divisible by zero
					alpha[a] = harvestMin2/0.001; //use a small number in lieu of zero
				}else {
					alpha[a] = harvestMin2/planHarvestVolume2[a];
				}
			}else {
				alpha[a] = 0.0;
			}			
		}
		return alpha;
	}

	/**
     * Retrieves the schedule or state with the maximum value for this cell object
     * @param id	the index of the cell or stand
     * @return 		an integer representing the maximum state of a cell
     */
	public int getMaxState(int id) {
		double maxValue = 0.0;
		double stateValue, isf,dsf;
		int stateMax =0;
		double[] lsn = new double[landscape.numTimePeriods];
		lsn = getNeighborLateSeral(cellsList.get(id).adjCellsList);
	
		/*for(int i = 0; i < cellsList.get(id).statesPrHV.size(); i++) { // Iterate through each of the plausible treatment schedules also known as states
		
			//isf = DoubleStream.of(multiplyVector(cellList.get(id).statesPrHV.get(i), sumVector(landscape.lambda, subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
			//dsf = DoubleStream.of(multiplyVector(divideScalar(sumVector(cellList.get(id).statesOG.get(i), lsn), landscape.numTimePeriods*2), sumVector(landscape.oneMinusLambda,landscape.gamma))).sum();
			isf = DoubleStream.of(multiplyVector(landscape.lambda,multiplyVector(cellsList.get(id).statesPrHV.get(i), subtractVector(landscape.alpha,landscape.beta)))).sum(); //I s(f) is the context independent component of the obj function			
			dsf = DoubleStream.of(multiplyVector(landscape.oneMinusLambda, multiplyVector(divideScalar(sumIntDoubleVector(cellList.get(id).statesOG.get(i), lsn), landscape.numTimePeriods*2.0), landscape.gamma))).sum();
			
			stateValue = isf + dsf;
			if(maxValue < stateValue) {
				maxValue = stateValue;
				stateMax = i;
			}
		};
		*/
		return stateMax;
	}
	
	/**
	 * Finds the proportion of a cells neighbours that are cut in the same time period
	 * @param adjCellsList
	 * @return a double representing the proportion of adjacent cells that are also cut in the same time period;
	 */
	private float[][] getNeighborProportion(ArrayList<Integer> adjCellsList) {
		float[][] hvn = new float[2][landscape.numTimePeriods];
		float[] tempHVN;
		float[] tempAgeN;
		int state = 0;
		
		for(int n =0; n < adjCellsList.size(); n++) {
			state = cellsList.get(adjCellsList.get(n)).state; // the cellList is no longer in order can't use get. Need a comparator.
			if(state == 0) {
				continue; // go to the next adjacent cell --this one has no harvesting
			}else {
				tempHVN = forestTypeList.get(cellsList.get(adjCellsList.get(n)).foresttype).stateTypes.get(state).get("harvVol");
				tempAgeN = forestTypeList.get(cellsList.get(adjCellsList.get(n)).foresttype).stateTypes.get(state).get("age");
				for(int t = 0 ; t < landscape.numTimePeriods; t ++) {
					if(tempHVN[t] > 0) {
						hvn[0][t]= hvn[0][t] + (float) 1/adjCellsList.size();
					}
					
					if(tempAgeN[t] > 100) {
						hvn[1][t]= hvn[1][t] + (float) 1/adjCellsList.size();
					}
				}
			}
		}		
		return hvn;
	}
	/**
     * Retrieves a factor between 0 and 1 that is equal to the proportion of stand f's neighbors 
     * that are also late-seral in planning period t
     * @param adjCellsList	an ArrayList of integers representing the cells index + 1
     * @return 		a vector of length equal to the number of time periods
     */
	public double[] getNeighborLateSeral(ArrayList<Integer> adjCellsList) {
		double[] lsn = new double[landscape.numTimePeriods];
		double lsnTimePeriod = 0.0;
		int counter = 0;
		
		for(int t =0; t < landscape.numTimePeriods; t++) {
			for(int n =0; n < adjCellsList.size(); n++) {
				int state = cellsList.get(adjCellsList.get(n)-1).state; // the cellList is no longer in order can't use get. Need a comparator.
				//lsnTimePeriod += cellsList.get(adjCellsList.get(n)-1).statesOG.get(state)[t];
				counter ++;
			}
			lsn[t] = lsnTimePeriod/counter;
			counter = 0;
			lsnTimePeriod = 0.0;
		}
		
		return lsn;
	}

	 /**
     * Multiplies two vectors together to return the element wise product.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param vector2	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see divideVector
     */
	private double[] multiplyVector (double[] vector1, double[] vector2) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]*vector2[i];
		}
		return outVector;
	}
	
	 /**
     * Divides two vectors together to return the element wise product.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param vector2	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see multiplyVector
     */
	private double[] divideIntVector (int[] vector1, int[] vector2) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = (double) vector1[i]/vector2[i];
		}
		return outVector;
	}
	
	 /**
     * Divides two vectors together to return the element wise product.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param vector2	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see multiplyVector
     */
	private double[] divideVector (double[] vector1, double[] vector2) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]/vector2[i];
		}
		return outVector;
	}
	
	
	 /**
     * Divides a vectors by a scalar element wise.
     * @param fs	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see multiplyScalar
     */
	private float[] divideScalar (float[] fs, double scalar) {
		float[] outVector = new float[fs.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = (float) ((float) fs[i]/scalar);
		}
		return outVector;
	}
	
	
	 /**
     * Multiplies a vectors by a scalar element wise.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see divideScalar
     */
	private double[] multiplyScalar (double[] vector1, double scalar) {
		double[] outVector = new double[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]*scalar;
		}
		return outVector;
	}
	
	 /**
     * Subtracts two vectors so that the element wise difference is returned. The first vector is subtracted by the second
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param vector2	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see sumVector
     */
	private int[] subtractIntVector (int[] vector1, int[] vector2) {
		int[] outVector = new int[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]-vector2[i];
		}
		return outVector;
	}
	 /**
     * Subtracts two vectors so that the element wise difference is returned. The first vector is subtracted by the second
     * @param planHarvestVolume2	an Array of doubles with length equal to the number of time periods
     * @param hashMap	an Array of doubles with length equal to the number of time periods
     * @return 		a vector of length equal to the number of time periods
     * @see sumVector
     */
	private float[] subtractVector (float[] planHarvestVolume2, float[] vol) {
		float[] outVector = new float[planHarvestVolume2.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = planHarvestVolume2[i]-vol[i];
		}
		return outVector;
	}
	
	 /**
     * Adds two vectors so that the element wise sum is returned.
     * @param planHarvestVolume2	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see subtractVector
     */
	private double sumArray(float[] vector) {
		float outValue = 0L;
		for(int i =0; i < vector.length; i++) {
			outValue = outValue + vector[i];
		}
		return outValue;
	}
	
	 /**
     * Adds two vectors so that the element wise sum is returned.
     * @param planHarvestVolume2	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see subtractVector
     */
	private float[] sumVector(float[] planHarvestVolume, float[] vector2) {
		float[] outVector = new float[planHarvestVolume.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = planHarvestVolume[i]+ vector2[i];
		}
		return outVector;
	}
	
	 /**
     * Adds two vectors so that the element wise sum is returned.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see subtractVector
     */
	private int[] sumIntVector(int[] vector1, int[] vector2) {
		int[] outVector = new int[vector1.length];
		for(int i =0; i < outVector.length; i++) {
			outVector[i] = vector1[i]+vector2[i];
		}
		return outVector;
	}
	 /**
     * Adds two vectors so that the element wise sum is returned.
     * @param vector1	an Array of doubles with length equal to the number of time periods
     * @param scalar	a scalar
     * @return 		a vector of length equal to the number of time periods
     * @see subtractVector
     */
	 private double[] sumIntDoubleVector(int[] vector1, double[] vector2) {
		 double[] outVector = new double[vector1.length];
			for(int i =0; i < outVector.length; i++) {
				outVector[i] = vector1[i]+vector2[i];
			}
		return outVector;
	}

	 /**
     * Instantiates java objects developed in R
     */
	public void setRParms(int[] to, int[] from, double[] weight, int[] dg, ArrayList<LinkedHashMap<String, Object>> histTable, double allowdiff ) {
		//Instantiate the Edge objects from the R data.table
		//System.out.println("Linking to java...");
		for(int i =0;  i < to.length; i++){
			 //this.edgeList.add( new Edges((int)to[i], (int)from[i], (double)weight[i]));
		}
		//System.out.println(to.length + " edges ");

		//this.degree = Arrays.stream(dg).boxed().toArray( Integer[]::new );
		//this.idegree = Arrays.stream(dg).boxed().toArray( Integer[]::new );
		//System.out.println(degree.length + " degree ");
		
		//this.hist = new histogram(histTable);
		//System.out.println(this.hist.bins.size() + " target bins have been added");
		
		//dg = null;
		//histTable.clear();
		//to = null;
		//from =null;
		//weight = null;
		
		//this.allowableDiff = allowdiff;
	}


	
	public void getCLUSData() throws Exception {
		try { // Load the driver
		    Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException eString) {
		    System.err.println("Could not init JDBC driver - driver not found");
		}		
		try { //Get the data from the db
			Connection conn = DriverManager.getConnection("jdbc:sqlite:C:/Users/klochhea/clus/R/SpaDES-modules/forestryCLUS/Quesnel_TSA_clusdb.sqlite");		
			if (conn != null) {
				System.out.println("Connected to clusdb");
				Statement statement = conn.createStatement();
				System.out.print("Getting yield information");
				//Create a yield lookup
				String yc_lookup = "CREATE TABLE IF NOT EXISTS yield_lookup as SELECT ROW_NUMBER() OVER( ORDER BY yieldid asc) as id_yc, yieldid, count(*) as num FROM yields GROUP BY yieldid;";
				statement.execute(yc_lookup);
				
				//Getting the yields--so far just age and height;
				String get_yc = "SELECT o.id_yc," +
						"  MAX(CASE WHEN p.age = 0 THEN p.tvol END) AS vol_0," + 
						"  MAX(CASE WHEN p.age = 10 THEN p.tvol END) AS vol_10," + 
						"  MAX(CASE WHEN p.age = 20 THEN p.tvol END) AS vol_20," + 
						"  MAX(CASE WHEN p.age = 30 THEN p.tvol END) AS vol_30," + 
						"  MAX(CASE WHEN p.age = 40 THEN p.tvol END) AS vol_40," + 
						"  MAX(CASE WHEN p.age = 50 THEN p.tvol END) AS vol_50," + 
						"  MAX(CASE WHEN p.age = 60 THEN p.tvol END) AS vol_60," + 
						"  MAX(CASE WHEN p.age = 70 THEN p.tvol END) AS vol_70," + 
						"  MAX(CASE WHEN p.age = 80 THEN p.tvol END) AS vol_80," + 
						"  MAX(CASE WHEN p.age = 90 THEN p.tvol END) AS vol_90," + 
						"  MAX(CASE WHEN p.age = 100 THEN p.tvol END) AS vol_100," + 
						"  MAX(CASE WHEN p.age = 110 THEN p.tvol END) AS vol_110," + 
						"  MAX(CASE WHEN p.age = 120 THEN p.tvol END) AS vol_120," + 
						"  MAX(CASE WHEN p.age = 130 THEN p.tvol END) AS vol_130," + 
						"  MAX(CASE WHEN p.age = 140 THEN p.tvol END) AS vol_140," + 
						"  MAX(CASE WHEN p.age = 150 THEN p.tvol END) AS vol_150," + 
						"  MAX(CASE WHEN p.age = 160 THEN p.tvol END) AS vol_160," + 
						"  MAX(CASE WHEN p.age = 170 THEN p.tvol END) AS vol_170," + 
						"  MAX(CASE WHEN p.age = 180 THEN p.tvol END) AS vol_180," + 
						"  MAX(CASE WHEN p.age = 190 THEN p.tvol END) AS vol_190," + 
						"  MAX(CASE WHEN p.age = 200 THEN p.tvol END) AS vol_200," + 
						"  MAX(CASE WHEN p.age = 210 THEN p.tvol END) AS vol_210," + 
						"  MAX(CASE WHEN p.age = 220 THEN p.tvol END) AS vol_220," + 
						"  MAX(CASE WHEN p.age = 230 THEN p.tvol END) AS vol_230," + 
						"  MAX(CASE WHEN p.age = 240 THEN p.tvol END) AS vol_240," + 
						"  MAX(CASE WHEN p.age = 250 THEN p.tvol END) AS vol_250," + 
						"  IFNULL(MAX(CASE WHEN p.age = 260 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_260," + 
						"  IFNULL(MAX(CASE WHEN p.age = 270 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_270," + 
						"  IFNULL(MAX(CASE WHEN p.age = 280 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_280," + 
						"  IFNULL(MAX(CASE WHEN p.age = 290 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_290," + 
						"  IFNULL(MAX(CASE WHEN p.age = 300 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_300," + 
						"  IFNULL(MAX(CASE WHEN p.age = 310 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_310," + 
						"  IFNULL(MAX(CASE WHEN p.age = 320 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_320," + 
						"  IFNULL(MAX(CASE WHEN p.age = 330 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_330," + 
						"  IFNULL(MAX(CASE WHEN p.age = 340 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_340," + 
						"  IFNULL(MAX(CASE WHEN p.age = 350 THEN p.tvol END), MAX(CASE WHEN p.age = 250 THEN p.tvol END)) AS vol_350," + 
						"  MAX(CASE WHEN p.age = 0 THEN p.height END) AS ht_0," + 
						"  MAX(CASE WHEN p.age = 10 THEN p.height END) AS ht_10," + 
						"  MAX(CASE WHEN p.age = 20 THEN p.height END) AS ht_20," + 
						"  MAX(CASE WHEN p.age = 30 THEN p.height END) AS ht_30," + 
						"  MAX(CASE WHEN p.age = 40 THEN p.height END) AS ht_40," + 
						"  MAX(CASE WHEN p.age = 50 THEN p.height END) AS ht_50," + 
						"  MAX(CASE WHEN p.age = 60 THEN p.height END) AS ht_60," + 
						"  MAX(CASE WHEN p.age = 70 THEN p.height END) AS ht_70," + 
						"  MAX(CASE WHEN p.age = 80 THEN p.height END) AS ht_80," + 
						"  MAX(CASE WHEN p.age = 90 THEN p.height END) AS ht_90," + 
						"  MAX(CASE WHEN p.age = 100 THEN p.height END) AS ht_100," + 
						"  MAX(CASE WHEN p.age = 110 THEN p.height END) AS ht_110," + 
						"  MAX(CASE WHEN p.age = 120 THEN p.height END) AS ht_120," + 
						"  MAX(CASE WHEN p.age = 130 THEN p.height END) AS ht_130," + 
						"  MAX(CASE WHEN p.age = 140 THEN p.height END) AS ht_140," + 
						"  MAX(CASE WHEN p.age = 150 THEN p.height END) AS ht_150," + 
						"  MAX(CASE WHEN p.age = 160 THEN p.height END) AS ht_160," + 
						"  MAX(CASE WHEN p.age = 170 THEN p.height END) AS ht_170," + 
						"  MAX(CASE WHEN p.age = 180 THEN p.height END) AS ht_180," + 
						"  MAX(CASE WHEN p.age = 190 THEN p.height END) AS ht_190," + 
						"  MAX(CASE WHEN p.age = 200 THEN p.height END) AS ht_200," + 
						"  MAX(CASE WHEN p.age = 210 THEN p.height END) AS ht_210," + 
						"  MAX(CASE WHEN p.age = 220 THEN p.height END) AS ht_220," + 
						"  MAX(CASE WHEN p.age = 230 THEN p.height END) AS ht_230," + 
						"  MAX(CASE WHEN p.age = 240 THEN p.height END) AS ht_240," + 
						"  MAX(CASE WHEN p.age = 250 THEN p.height END) AS ht_250," + 
						"  IFNULL(MAX(CASE WHEN p.age = 260 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_260," + 
						"  IFNULL(MAX(CASE WHEN p.age = 270 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_270," + 
						"  IFNULL(MAX(CASE WHEN p.age = 280 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_280," + 
						"  IFNULL(MAX(CASE WHEN p.age = 290 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_290," + 
						"  IFNULL(MAX(CASE WHEN p.age = 300 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_300," + 
						"  IFNULL(MAX(CASE WHEN p.age = 310 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_310," + 
						"  IFNULL(MAX(CASE WHEN p.age = 320 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_320," + 
						"  IFNULL(MAX(CASE WHEN p.age = 330 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_330," + 
						"  IFNULL(MAX(CASE WHEN p.age = 340 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_340," + 
						"  IFNULL(MAX(CASE WHEN p.age = 350 THEN p.height END), MAX(CASE WHEN p.age = 250 THEN p.height END)) AS ht_350" + 
						" FROM yield_lookup o " + 
						" JOIN yields p " + 
						"  ON o.yieldid = p.yieldid " + 
						" GROUP BY o.id_yc ORDER BY o.id_yc;";
				ResultSet rs0 = statement.executeQuery(get_yc);
				yieldTable.add(0, new HashMap<String, float[]>());
				int id_yc = 1;
				
				while(rs0.next()) {
					yieldTable.add(id_yc, new HashMap<String, float[]>());
					float[] vol = new float[36];
					float[] ht = new float[36];
					for(int y =0; y < 36; y++) {
						vol[y] = rs0.getFloat(y+2);//the first one is the id the second starts the 36 yields
						ht[y] = rs0.getFloat(y+38); //the yields for height are the next 36 yields
					}
					yieldTable.get(id_yc).put("vol", vol);
					yieldTable.get(id_yc).put("height", ht);
					id_yc ++;
				}
				System.out.println("...done");
				
				System.out.print("Getting state information");
				//Create manage_type field to check what type of management the cell has.
				// manage_type : -1 means non forested; 0: means forested but not harvestable; 1: forest and harvestable
				String manage_type = "SELECT COUNT(*) FROM pragma_table_info('pixels') WHERE name='manage_type';";
				ResultSet rs1 = statement.executeQuery(manage_type);
				if(rs1.getInt(1) == 0) { //only populate if the pixels table has no records in it
					//create a column in the pixels table called type
					String add_column1 = "ALTER TABLE pixels ADD COLUMN manage_type integer default -1;";
					statement.execute(add_column1);
					String populate_type0 = "UPDATE pixels SET manage_type = 0 where age is not null and yieldid is not null;";
					statement.execute(populate_type0 );
					String populate_type1 = "UPDATE pixels SET manage_type = 1 where thlb > 0 and age is not null and yieldid is not null and yieldid_trans is not null;";
					statement.execute(populate_type1 );				
				}
				
				//Create the 'foresttype' table and populate it with unique forest types
				String create_foresttype = "CREATE TABLE IF NOT EXISTS foresttype AS " +
						" SELECT ROW_NUMBER() OVER(ORDER BY age asc, yield_lookup.id_yc, t.id_yc_trans, pixels.manage_type) AS foresttype_id, age, id_yc, id_yc_trans, pixels.manage_type, pixels.yieldid, pixels.yieldid_trans " + 
						" FROM pixels " + 
						" LEFT JOIN yield_lookup ON pixels.yieldid = yield_lookup.yieldid" + 
						" LEFT JOIN (SELECT id_yc AS id_yc_trans, yieldid FROM yield_lookup) t ON pixels.yieldid_trans = t.yieldid " + 
						" WHERE manage_type > -1 AND id_yc IS NOT null GROUP BY age, id_yc, id_yc_trans, manage_type;";	
				statement.execute(create_foresttype);
				
				//Set the states for each foresttype
				String getForestType = "SELECT foresttype_id, age, id_yc, id_yc_trans, manage_type FROM foresttype ORDER BY foresttype_id;";
				ResultSet rs2 = statement.executeQuery(getForestType);
				forestTypeList.add(0, new ForestType()); //at id zero there is no foresttype -- add a null
				int forestTypeID = 1;
				while(rs2.next()) {
					if(forestTypeID == rs2.getInt(1)) {
						ForestType forestType = new ForestType(); //create a new ForestType object
						forestType.setForestTypeAttributes(rs2.getInt(1), Math.min(350, rs2.getInt(2)), rs2.getInt(3), rs2.getInt(4), rs2.getInt(5)); //the max age a cell can have is 350
						forestType.setForestTypeStates(rs2.getInt(5), landscape.ageStatesTemplate.get(Math.min(350, rs2.getInt(2))), landscape.harvestStatesTemplate.get(Math.min(350, rs2.getInt(2))), yieldTable.get(rs2.getInt(3)),  yieldTable.get(rs2.getInt(4)), landscape.minHarvVol);
						forestTypeList.add(forestTypeID, forestType);
					}else {
						throw new Exception("forestTypeID does not coincide with the forestTypeList! Thus, the list of forestTypes will be wrong");
					}
					forestTypeID ++;
				}
				System.out.println("...done");
				
				System.out.print("Getting spatial information");
				//Get raster information used to set the GRID object. This is important for determining adjacency
				String getRasterInfo = "SELECT ncell, nrow FROM raster_info";
				ResultSet rs3 = statement.executeQuery(getRasterInfo);
				while(rs3.next()) {
					landscape.setGrid(rs3.getInt("ncell"), rs3.getInt("nrow"));
				}
				System.out.println("...done");
				
				System.out.print("Getting cell information");			
				int[] pixelidIndex = new int[landscape.ncell + 1];
				for(int i =0; i < landscape.ncell+1; i++) {
					pixelidIndex[i] = i; //this will be used for adjacency after the Cells objects are instantiated
				}
				int[] cellIndex = new int[landscape.ncell + 1]; // since pixelid starts one rather than zero, add a one
			    Arrays.fill(cellIndex, -1); // used to look up the index from the pixelid   
				
			    
			    
			    //Instantiate the Cells objects for each cell that is forested og manage_type >= 0
				String getAllCells = "SELECT pixelid, pixels.age, foresttype.foresttype_id,  pixels.manage_type, thlb "
						+ "FROM pixels "
						+ "LEFT JOIN foresttype ON pixels.age = foresttype.age AND "
						+ "pixels.yieldid = foresttype.yieldid AND pixels.yieldid_trans = foresttype.yieldid_trans "
						+ "AND pixels.manage_type = foresttype.manage_type "
						+ "WHERE pixels.yieldid IS NOT null AND "
						+ "pixels.age IS NOT null AND "
						+ "foresttype.foresttype_id IS NOT null "
						+ "ORDER BY pixelid;";
				ResultSet rs4 = statement.executeQuery(getAllCells);
				int counter = 0; // this is the index for cellsList -- the list of Cells objects
				while(rs4.next()) { // not all cells get a state of zero initially -- useful for inferring the landCoverConstraints ---years of recruitment
					cellsList.add(new Cells(rs4.getInt(1), rs4.getInt(2), rs4.getInt(3), rs4.getInt(4), rs4.getFloat(5)));
					cellIndex[rs4.getInt(1)] = counter;
					counter ++;
				}				
				System.out.println("...done");
				
				System.out.print("Getting constraint information");
				landCoverConstraintList.add(0, new LandCoverConstraint()); // zero is a null landCoverConstraint
				counter =0;
				String getConstraintObjects = "SELECT id, variable, threshold, type, percentage, t_area FROM zoneConstraints ORDER BY id;";
				ResultSet rs5 = statement.executeQuery(getConstraintObjects);
				while(rs5.next()) {
					counter ++;
					if(counter == rs5.getInt(1)) {
						landCoverConstraintList.add(counter, new LandCoverConstraint());
						landCoverConstraintList.get(counter).setLandCoverConstraintParameters(rs5.getString(2), rs5.getFloat(3),rs5.getString(4), rs5.getFloat(5), rs5.getFloat(6), landscape.numTimePeriods);
					}else {
						throw new Exception("zoneConstraint id does not coincide with the landCoverConstraintList! Thus, the list of constraints will be wrong");
					}		
				}
				System.out.println("...done");
				
				System.out.print("Setting constraints to cells");
				String setConstraints = "SELECT zone_column FROM zone WHERE reference_zone = 'rast.zone_cond_beo';";
				ResultSet rs6 = statement.executeQuery(setConstraints);
				ArrayList<String> zones = new ArrayList<String>();
				while(rs6.next()) {
					zones.add(rs6.getString(1));
				}
				for(int z = 0; z < zones.size(); z++) {
					String getZonesConstraints = "SELECT pixelid, z.id "
							+ "FROM pixels "
							+ "LEFT JOIN (SELECT * FROM zoneConstraints WHERE zone_column = '" +zones.get(z)+ "') AS z "
							+ "ON pixels." + zones.get(z) +" = z.zoneid "
							+ "WHERE pixels."+zones.get(z)+" is not null;";
					ResultSet rs7 = statement.executeQuery(getZonesConstraints);
					int cell;
					while(rs7.next()) { 
						cell = cellIndex[rs7.getInt(1)];
						if(cell >= 0) { //removes non-treed area (-1) with no state changes
							cellsList.get(cell).setLandCoverConstraint(rs7.getInt(2));
						}
					}
				}
				System.out.println("...done");

				//Close all connections to the clusdb	
				statement.close();
				conn.close();
				System.out.println("Disconnected from clusdb");
				
				System.out.print("Setting neighbourhood information");	
				int cols = (int) landscape.ncell/landscape.nrow;
				for(int c = 0; c < cellsList.size(); c++) {
					ArrayList<Integer> adjList = new ArrayList<Integer>();
					adjList = getNeighbourhood(cellsList.get(c).pixelid, cols , pixelidIndex, cellIndex);
					cellsList.get(c).setNeighbourhood(adjList);
				}
				
				
				
				System.out.println("...done");
			} 
              
        }catch (SQLException e) {
            System.out.println(e.getMessage());
        }
	}

	private ArrayList<Integer> getNeighbourhood(int id, int cols, int[] pixelidIndex, int[] cellIndex) {
	    ArrayList<Integer> cs = new ArrayList<Integer>(8);
	    //check if cell is on an edge
	    boolean l = id %  cols > 0;        //has left
	    boolean u = id >= cols;            //has upper
	    boolean r = id %  cols < cols - 1; //has right
	    boolean d = id <   pixelidIndex.length - cols;   //has lower
	    //collect all existing adjacent cells
	    if (l && cellIndex[pixelidIndex[id - 1]] > 0) {
	    	cs.add(cellIndex[pixelidIndex[id - 1]] );
	    }
	    if (l && u && cellIndex[pixelidIndex[id - 1 - cols]] > 0) {
	    	cs.add(cellIndex[pixelidIndex[id - 1 - cols]]);
	    }
	    if (u && cellIndex[pixelidIndex[id     - cols]] > 0) {
	    	cs.add(cellIndex[pixelidIndex[id     - cols]]);
	    }
	    if (u && r && cellIndex[pixelidIndex[id + 1 - cols]] > 0) {
	    	cs.add(cellIndex[pixelidIndex[id + 1 - cols]]);
	    }
	    if (r && cellIndex[pixelidIndex[id + 1       ]] > 0)     {
	    	cs.add(cellIndex[pixelidIndex[id + 1       ]]);
	    }
	    if (r && d && cellIndex[pixelidIndex[id + 1 + cols]] > 0) {
	    	cs.add(cellIndex[pixelidIndex[id + 1 + cols]]);
	    }
	    if (d && cellIndex[pixelidIndex[id     + cols]] > 0)      {
	    	cs.add(cellIndex[pixelidIndex[id     + cols]]);
	    }
	    if (d && l && cellIndex[pixelidIndex[id - 1 + cols]] > 0) {
	    	cs.add(cellIndex[pixelidIndex[id - 1 + cols]]);
	    }
	    
	    return cs;
	}

}



