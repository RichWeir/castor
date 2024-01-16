[![img](https://img.shields.io/badge/Lifecycle-Experimental-339999)](https://github.com/bcgov/repomountie/blob/master/doc/lifecycle-badges.md)

## Welcome to FLEX! The Fisher Landscape EXplorer - agent based model

### Introduction
The *FLEX* module was developed to estimate the effects of forest change on fisher populations. It is an agent-based model, which are models that simulate the actions of agents (in this case, individual fishers) in response to their environment (i.e., habitat) based on their ecology and behaviour. Therefore, it is a 'bottom-up' approach, where the behaviours of individuals are simulated to understand the collective impacts to a population.

Here we describe the logic of how *FLEX* simulates the ecology and behaviour of individual fishers. *FLEX* works with within the Castor set of models through the *fisherHabitatCastor* module, where *fisherHabitatCastor* estimates fisher habitat attributes from forest stand attributes that are estimated from *forestryCastor* (a forest simulator). Therefore, the fisher agents simulated in *FLEX* interact with landscapes simulated from *forestryCastor*. This version of the model only simulates female fishers, and therefore assumes the landscape does not affect interactions, and thus mating rates, between males and females.  

The *FLEX* module consists of an initiation (init) phase that establishes fisher on the landscape, and an annual simulation phase, that iteratively simulates individual fisher life-history events and behaviours over the course of a year, including survival, dispersal and territory formation, reproduction and aging. Below we describe these phases and events in more detail.

### Habitat Input to FLEX
The *FLEX* module interacts with the landscape and fisher habitat though the habitat data parameter (rasterHabitat), which is specified as a multi-band raster in Tag Image File Format (TIFF). This includes data on the location of fisher habitat sub-populations, and the location (presence or absence) of each fisher habitat type (denning, rust, movement, coarse-woody debris, cavity and open) across the simulation area of interest for each simulation interval, as specified in the forest simulator (i.e., *forestryCastor*). Therefore, it provides estimates of the location of key fisher habitat types over time as it changes in response to forest dynamics, including potentially disturbances such as forestry or fire. The rasterHabitat parameter is provided as an output from *fisherHabitatCastor* when run concurrently with *forestryCastor*. 

### Initiation of the Fisher Population 
The *FLEX* module starts by establishing a fisher population on the landscape, which consists of placing adult female fishers within denning habitat across the landscape. The general objective of the init phase is to 'saturate' the landscape with fishers, i.e., identify the number of fisher territories that can be supported by the habitat in the landscape. To do that, the init phase identifies the number of fishers that potentially could be supported on the landscape, distributes them across the landscape based on the distribution of fisher denning habitat, and then determines which fishers can form a territory. The result of that process is a landscape at or close to its maximum potential for fisher occupancy. Therefore the assumed starting point of the model is a fisher population at its carrying capacity for the habitat in the landscape. 

#### Identifying the Number of Fishers to Place on the Landscape
The user can either specify the number of fishers to distribute on the landscape using the initialFisherPop, or estimate the number of fishers to distribute on the landscape based on the average fisher home range size and the distribution of denning habitat across the study area. We currently recommend using the latter approach to ensure that a saturated landscape is achieved. 

To estimate the number of fishers on the landscape, first the model aggregates the existing pixels (typically 1 ha) in the area of interest into larger pixels with the size of the average home range for fishers across all fisher habitat types (i.e., 3,750 ha). Then the number of aggregated pixels with greater than 1% denning habitat within them are summed to determine the number of fishers to distribute across the landscape. 

#### Distributing Fishers Across the Landscape
Fishers are randomly distributed across the landscape following a well-balanced sampling design (see: [Brus 2003](https://dickbrus.github.io/SpatialSamplingwithR/BalancedSpreaded.html#LPM)). Specifically, the local pivot method was used, where the first location was selected randomly, and then additional locations are selected where locations in closer proximity are less likely to be selected, until the target number of locations (i.e., the number of fishers) are met. The method also considers inclusion probabilities, which are currently set as equal across all locations.

#### Fisher Agent Creation
Once the starting number of fishers are distributed across the landscape, an "agents" table is created where each starting fisher receives a unique identifier, the location on the landscape (pixelid) of its starting point, a sex (currently all fishers in the model are female) and an age, selected randomly, based on the estimated distribution of fisher ages from populations in British Columbia (see table below).


```r
data <- data.table (age = c(1,2,3,4,5,6,7,8,9,10,11,12), 
                    prob = c(0.44, 0.27, 0.08, 0.08, 0.06, 0.03, 0.02, 
                             0.01,0.0025,0.0025,0.0025,0.0025))

ggplot (data,
        aes (x = age, y = prob)) + 
  geom_bar (stat ="identity")
```

![](README_FLEX_files/figure-html/age distribution-1.png)<!-- -->

Each fisher in the agents table then gets assigned a target amount of habitat it needs to from a territory. In this model, the quality of habitat is not quantified. Therefore rather than have the fisher form a territory by aggregating habitat until it achieves a specified, minimum amount of quality habitat, the amount of habitat each fisher needs is assigned randomly. *TBD: The amount of habitat each fisher is assigned is drawn from a normal distribution of the amount and variability in habitat from known fisher home ranges (see table below).* Note that in this case, the amount of habitat in a territory may be less than estimated home range sizes for fisher, depending on the method used (e.g., kernel density estimator or minimum convex polygon), and specifically if the home range estimate included areas that are not necessarily fisher habitat.


```r
data.table (fisher_pop = c (1:4), 
            hr_mean = c (3000, 4500, 4500, 3000),
            hr_sd = c (500, 500, 500, 500))
```

```
##    fisher_pop hr_mean hr_sd
## 1:          1    3000   500
## 2:          2    4500   500
## 3:          3    4500   500
## 4:          4    3000   500
```

#### Fisher Territory Formation
Each fisher in the agents table then forms a territory by 'spreading' from it's starting den site location to adjacent habitat, until it achieves it's habitat target, or it is no longer able to spread to adjacent areas because it is not habitat. The model uses the [spread2 function](https://rdrr.io/cran/SpaDES.tools/man/spread2.html) in the [SpaDES R package](https://spades.predictiveecology.org/) to form territories, where the  



habitat data parameter (rasterHabitat)


sim$spread.rast, 
                                       start = sim$agents$pixelid, 
                                       spreadProb = as.numeric (sim$spread.rast[]),
                                       exactSize = sim$agents$hr_size, 
                                       allowOverlap = F, asRaster = F, circle = F)
                                       
                                       
                                       


Spread to form territories
s
-	Open = spread_prob 0.09
-	No habtiat = spread_prob 0.18
-	Habtait spread = spread_prob 1
-	Check to see if achieved mean hr size – 2 sd and amount of habtiat 15% 

-	Meet tthrehdolsds of habtait caterogies and d2 therhfolds as set by the user
-	if animal doesn’t meet the threholds then ift gets put into the diespersers table











### Input Parameters
The *FLEX* module requires that the user specify the fisher habitat data and several ecological parameters for fisher.



Survival parameters consist of a table (survival_rate_table) that is 'hard-coded' in the model (i.e., it currently cannot be directly input by the user). Survival rates are defined by age class, for each population (boreal, sub-boreal moist, sub-boreal dry, and dry forest) and for dispersers and non-dispersers (i.e., animals with a territory). The age classes are 1 to 2 years old, 3 to 5 years old, 5 to 8 years old and greater than 8 years old (all classes are inclusive). Therefore, there are thirty-two survival rates in the table for each population, age class and disperser class. Note that animals less than 1 year old do not have a survival rate, as survival is a component of the recruitment rate (see below).

Similar to the survival parameters, the reproductive parameters consist of table of recruitment rates (repro_rate_table) that is 'hard-coded' in the model. A rate is specified for each population and thus there four recruitment rates in the table. These recruitment rates represent the probability that kit survives to a juvenile (age 1). For reproduction, the user can also specify a sex ratio (sex_ratio) for assigning the proportion of kits that are female (the default value is 0.5), and the minimum age a aisher has to be to reproduce (reproductive_age; the default value is 2 years old). 







    defineParameter("female_dispersal", "numeric", 785000, 100, 10000000,"The area, in hectares, a fisher could explore during a dispersal to find a territory."),
    

    
    
    defineParameter("timeInterval", "numeric", 1, 1, 20, "The time step, in years, when habtait was updated. It should be consistent with periodLength form growingStockCASTOR. Life history events (reproduce, updateHR, survive, disperse) are calaculated this many times for each interval."),
    
    
    defineParameter("den_target", "numeric", 0.001, 0, 1,"The minimum proportion of a home range that is denning habitat. Values taken from empirical female home range data across populations."), 
    defineParameter("rest_target", "numeric", 0.001, 0, 1, "The minimum proportion of a home range that is resting habitat. Values taken from empirical female home range data across populations."),   
    defineParameter("move_target







FLEX2 documentation
Finds number of potential territories using the aggregation 
Initialization 
Aggregation to define the number of fisher on the landcapes
-	Gagreagte pixels to average home range size
-	Check if there is a dennign pixel in it,
-	- count nymebr of pixels with a dennign pixel 
-	That is the number of fisher to star-
-	 or can input directly 
 



Dispersers table
-	Attributes of dispersers without a territory 

Table_hab_spread
-	Updated, conditional on habtait classes




Then dispersers attempt to find a new territory 
-	Finds dennign pixels not in a territory
-	Identifies potential # terriotires using eth ggregation function
o	If more dispersers than potential, then some fisher sdont; get a locations
-	Then use well spread sampling to disperse fisher
-	 Finds the closest denning site for a fisher; the closest fisher gets the dennign site
-	Adults get priority over juveniles in the denning too 
-	Successful dispersers then attempt to spread a territory and check that needs are met

Annual time step
-	Update habitat
-	Check if habitat is still high enough quality
-	Then loop of survive, dispersal, territory formation, reproduce, ,age and then report
-	

Reproduction
-	Includes fertility rate and juvenile survival (i.e., recruitment rate)
-	So reproducers are actually recruitment rate, so more kits were born than estimated as reporducing


Survival first
Age and terriorty based survival rates
1-2, 3-5, 5-8, >8
Dispersers and non-dispersers
efefcts of habitat on survival is through the transition from an animal with a terriotry to a disperers, i.e., if the habtiat quliuty of a home rnage degrades to the poiint the anaimal disperses, then the animal gets a lower surival rate  


