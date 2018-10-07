<?xml version="1.0" encoding="UTF-8"?>
<plannerBenchmark>
	<parallelBenchmarkCount>AUTO</parallelBenchmarkCount>
  <benchmarkDirectory>local/data/workerrostering</benchmarkDirectory>
  <warmUpSecondsSpentLimit>30</warmUpSecondsSpentLimit>
	
  <inheritedSolverBenchmark>
    <problemBenchmarks>
      <solutionFileIOClass>org.optaplanner.training.workerrostering.persistence.WorkerRosteringSolutionFileDaysIO</solutionFileIOClass>
      <inputSolutionFile>data/workerrostering/import/roster_anna_9.xlsx</inputSolutionFile>
      <problemStatisticType>BEST_SCORE</problemStatisticType>
      <singleStatisticType>CONSTRAINT_MATCH_TOTAL_BEST_SCORE</singleStatisticType>
    </problemBenchmarks>
    <solver>
      <scanAnnotatedClasses/>

      <scoreDirectorFactory>
        <scoreDrl>org/optaplanner/training/workerrostering/solver/workerRosteringScoreRules.drl</scoreDrl>
      </scoreDirectorFactory>

      <termination>
        <secondsSpentLimit>2000</secondsSpentLimit>
      </termination>
    </solver>
    <subSingleCount>1</subSingleCount>
  </inheritedSolverBenchmark>

  <solverBenchmark>
    <name>Only Construction Heuristic</name>
    <solver>
      <constructionHeuristic/>
    </solver>
  </solverBenchmark>
  <!--  same as tabu no config? <solverBenchmark>
    <name>Local Search too</name>
    <solver/>
  </solverBenchmark>
  -->
  <solverBenchmark>
    <name>tabu no config</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>

    <localSearchType>TABU_SEARCH</localSearchType>
  </localSearch>
  </solver>
  </solverBenchmark>

  <#list [5, 7] as entityTabuSize>
	<#list [500, 1000, 2000] as acceptedCountLimit>
	<solverBenchmark>
    <name>tabu ${entityTabuSize} ${acceptedCountLimit}</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
          <entityTabuSize>${entityTabuSize}</entityTabuSize>
        </acceptor>
        <forager>
          <acceptedCountLimit>${acceptedCountLimit}</acceptedCountLimit>
        </forager>
  </localSearch>
  </solver>
  </solverBenchmark>
  </#list>
</#list>
  <solverBenchmark>
    <name>tabu 7  with LA 200</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
                <entityTabuSize>7</entityTabuSize>
      <lateAcceptanceSize>200</lateAcceptanceSize>
        </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
  <solverBenchmark>
    <name>tabu 7  with LA 100</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
                <entityTabuSize>7</entityTabuSize>
      <lateAcceptanceSize>100</lateAcceptanceSize>
        </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
  <solverBenchmark>
    <name>tabu 5 th LA 100</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
                <entityTabuSize>5</entityTabuSize>
      <lateAcceptanceSize>100</lateAcceptanceSize>
        </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
  
  <solverBenchmark>
    <name>tabu 9  with LA 100</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
                <entityTabuSize>9</entityTabuSize>
      <lateAcceptanceSize>100</lateAcceptanceSize>
        </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
  
  <solverBenchmark>
    <name>tabu 7  with LA 150</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
                <entityTabuSize>7</entityTabuSize>
      <lateAcceptanceSize>150</lateAcceptanceSize>
        </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
  <solverBenchmark>
    <name>tabu 7  with LA 50</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
                <entityTabuSize>7</entityTabuSize>
      <lateAcceptanceSize>50</lateAcceptanceSize>
        </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
  <solverBenchmark>
    <name>tabu 7  with LA 500</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
   <acceptor>
                <entityTabuSize>7</entityTabuSize>
      <lateAcceptanceSize>500</lateAcceptanceSize>
        </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
   <solverBenchmark>
    <name>tabu 7  200 strategic oscillation</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
    <!--  <unionMoveSelector>
      <changeMoveSelector/>
      <swapMoveSelector/>
    </unionMoveSelector>-->
    <acceptor>
      <entityTabuSize>7</entityTabuSize>
      <lateAcceptanceSize>200</lateAcceptanceSize>
    </acceptor>
     <forager>
      <acceptedCountLimit>1000</acceptedCountLimit>
      <finalistPodiumType>STRATEGIC_OSCILLATION</finalistPodiumType>
    </forager>
  </localSearch>
  </solver>
  </solverBenchmark>
  <solverBenchmark>
    <name>tabu 14  400</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
  <localSearch>
    <!--  <unionMoveSelector>
      <changeMoveSelector/>
      <swapMoveSelector/>
    </unionMoveSelector>-->
    <acceptor>
      <entityTabuSize>14</entityTabuSize>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
  </localSearch>
  </solver>
  </solverBenchmark>
  <solverBenchmark>
    <name>tabu accepted count 5000</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
	  <localSearch>
		<forager>
		  <acceptedCountLimit>5000</acceptedCountLimit>
		</forager>
	  </localSearch>
  </solver>
  </solverBenchmark>
<solverBenchmark>
    <name>tabu accepted count 500</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
	  <localSearch>
    <forager>
      <acceptedCountLimit>500</acceptedCountLimit>
    </forager>
  </localSearch>
  </solver>
  </solverBenchmark>
  <solverBenchmark>
    <name>simulated annealing test</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
	  <localSearch>
    <acceptor>
      <simulatedAnnealingStartingTemperature>20hard/8000medium/10000soft</simulatedAnnealingStartingTemperature>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>
  </solver>
  </solverBenchmark>
   <solverBenchmark>
    <name>late acceptance test with tabu</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
	  <localSearch>
    <acceptor>
      <lateAcceptanceSize>400</lateAcceptanceSize>
      <entityTabuSize>5</entityTabuSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>
  </solver>
  </solverBenchmark>
     <solverBenchmark>
    <name>late acceptance test without tabu</name>
    <solver>
      <constructionHeuristic>
		<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
	  </constructionHeuristic>
	  <localSearch>
    <acceptor>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>
  </solver>
  </solverBenchmark>
</plannerBenchmark>
