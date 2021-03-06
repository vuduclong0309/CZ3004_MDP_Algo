package grp15.algorithm;

import grp15.object.Cell;
import grp15.object.RobotOrientation;
import grp15.rpi.Comms;
import grp15.simulator.MazeSolver;
import grp15.util.MapDescriptor;
import javafx.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import static grp15.object.Cell.GRID_SIZE;
import static grp15.object.Robot.NORTH;
import static grp15.object.Robot.WEST;
import static grp15.simulator.MazeEditor.MAZE_WIDTH;
import static grp15.simulator.MazeEditor.MAZE_HEIGHT;
import static grp15.object.Robot.isValidPosition;

public class Explorer {

    public double coverageThreshold = 0.5;
    public static int WAYPOINT_X = 8;
    public static int WAYPOINT_Y = 8;
    public static int SPEED = 10;
    static DijkstraSolver solver;
    private MazeSolver map;
    private JFrame frame;
    boolean startFP = false;
    boolean visited [][][] = new boolean[MAZE_HEIGHT][MAZE_WIDTH][4];
    private boolean timeout = false;

    public Explorer(MazeSolver m) {
        this.map = m;
    }

    public void launch(){
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                frame = new JFrame("Explorer");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.getContentPane().add(BorderLayout.CENTER, map);
                frame.setResizable(false);
                frame.setSize(new Dimension(MAZE_WIDTH * (GRID_SIZE+1), MAZE_HEIGHT * (GRID_SIZE+2)));
                //setting the window location


                JButton startFastestPathButton = new JButton ("Start Fastest Path");
                startFastestPathButton.addActionListener(new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        startFP = true;
                    }

                });

                frame.setLocationByPlatform(false);
                frame.setLocation(0, 0);
                frame.setVisible(true);
                frame.getContentPane().add(startFastestPathButton, BorderLayout.SOUTH);


                startExploration();
                while(startFP == false) {
                    try {
                        System.out.println("waiting for fastest path button");
                        TimeUnit.SECONDS.sleep(1);
                    }
                    catch (Exception e){

                    }
                };
                startFastestPath();
            }
        }  );
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.start();

    }

    public void startExploration(){

        map.senseMap();
        this.map.repaint();
        int i = 0;
        boolean init = true;

        LeftWallHuggingSolver wallHuggingSolver = new LeftWallHuggingSolver(map.getMazeCell(), this.map.getRobot());
        do{
            ArrayList<Integer> path = wallHuggingSolver.getBurstMove(map, new RobotOrientation(map.getRobot()));
            String signal = FastestPathAlgorithm.movePathToSignalString(path) + 'o';
            System.out.println("Signal String: " + signal);
            for(int j = 0; j < path.size(); j++){
                visited[wallHuggingSolver.getRobot().getPosX()][wallHuggingSolver.getRobot().getPosY()][wallHuggingSolver.getRobot().getDirection()] = true;
                System.out.println(wallHuggingSolver.getRobot().getPosX() + " " + wallHuggingSolver.getRobot().getPosY() + " " + path.get(j));
                map.getRobot().moveRobot(path.get(j));
                map.senseMap();
                //for(int k=1;k<=100;k++) System.out.println("pause thread");
                this.map.repaint();
                try {
                    Thread.sleep(1000/SPEED);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            System.out.println("New Robot Pos " + map.getRobot().getOrientation().toPairFormat().toString());
        }while(!map.getRobot().getOrientation().isEqual(new RobotOrientation(1, 1, 3)));
        System.out.println("wall hug done");

        HashMap<Pair<Pair<Integer, Integer>, Integer>, Pair<Integer, Integer>> distanceMap;

        solver = new DijkstraSolver(map.getMazeCell(), 1, 1, this.map.getRobot());
        System.out.println(map.coverage());
        do{
            System.out.println("iteration"+i);
            i++;
            distanceMap = solver.getDistanceMap();
            HashMap.Entry<Pair<Pair<Integer, Integer>, Integer>, Pair<Integer, Integer>> nextPosMinDistance = null;
            double gridIndex = 0;
            for(HashMap.Entry<Pair<Pair<Integer, Integer>, Integer>, Pair<Integer, Integer>> entry: distanceMap.entrySet()){

                int nextPosX = entry.getKey().getKey().getKey();
                int nextPosY = entry.getKey().getKey().getValue();
                int direction = entry.getKey().getValue();
                int distance = entry.getValue().getKey();
                int newIndex = falseSense(nextPosX, nextPosY, direction, map.getMazeCell()); // == 0 && !(nextPosX == 1 && nextPosY == 1)) continue;
                //init = false;
                System.out.println("entry"+entry.toString() + "@" + newIndex);
                if(newIndex == 0) continue;
                if (nextPosMinDistance == null){

                    if (visited[nextPosX][nextPosY][direction] == false) {
                        nextPosMinDistance = entry;
                        gridIndex = (double)(newIndex*newIndex)/(distance*distance*distance*distance);
                    }
                } else if (gridIndex < (double)(newIndex*newIndex)/(distance*distance*distance*distance)){
                    //System.out.println(nextPosMinDistance.toString());

                    if (visited[nextPosX][nextPosY][direction] == false) {
                        nextPosMinDistance = entry;
                        gridIndex = (double)(newIndex*newIndex)/(distance*distance*distance*distance);
                    }
                }
            }
            System.out.println("done entry");
            if(nextPosMinDistance == null){
                System.out.println("Exploration Completed");
                break;
            }
            //System.out.println(solution.get(new RobotOrientation(robot).toPairFormat()));
            ArrayList<Integer> path = solver.getPathFromDistanceMap(distanceMap, new RobotOrientation(solver.getRobot()), new RobotOrientation(nextPosMinDistance.getKey()));
            visited[solver.getRobot().getPosX()][solver.getRobot().getPosY()][solver.getRobot().getDirection()] = true;
            for(int j = 0; j < path.size(); j++){
                System.out.println(solver.getRobot().getPosX() + " " + solver.getRobot().getPosY() + " " + path.get(j));
                map.getRobot().moveRobot(path.get(j));
                map.senseMap();
                //for(int k=1;k<=100;k++) System.out.println("pause thread");
                this.map.repaint();
                visited[solver.getRobot().getPosX()][solver.getRobot().getPosY()][solver.getRobot().getDirection()] = true;
                try {
                    Thread.sleep(1000/SPEED);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if(map.coverage() > coverageThreshold){
                break;
            }

            //System.out.println("robot position" + solver.getRobot().getPosX() + solver.getRobot().getPosY() + solver.getRobot().getDirection());
        }while(timeout == false);

        String [] finalMap = MapDescriptor.generateMapDescriptor(map);
        String finalMapAndroid = MapDescriptor.toAndroid(map);
        System.out.println(Comms.FINAL_MAP + " " + finalMap[0] + "\n" + finalMap[1]);
        System.out.println("Turn and Move" + map.getRobot().getTotalMove() + " " + map.getRobot().getTotalTurn());
        FastestPathAlgorithm pathAlgorithm = new FastestPathAlgorithm(solver);
        ArrayList<Integer> backToStart = pathAlgorithm.getFastestPath(new RobotOrientation(map.getRobot()), new RobotOrientation(new Pair(new Pair(1, 1), WEST)));
        pathAlgorithm.moveRobotbyPath(backToStart, map, false);

    }

    void startFastestPath() {
        map.getRobot().setPosRaw(1, 1, NORTH);
        map.repaint();
        try {
            TimeUnit.SECONDS.sleep(4);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        FastestPathAlgorithm pathAlgorithm = new FastestPathAlgorithm(solver);
        HashMap<Pair<Pair<Integer, Integer>, Integer>, Pair<Integer, Integer>> distanceMap;
        distanceMap = solver.getDistanceMap();

        HashMap.Entry<Pair<Pair<Integer, Integer>, Integer>, Pair<Integer, Integer>> nextPosMinDistance = null;
        int minDistance = 10000;
        int minWaypointDislocation = 100;
        for (HashMap.Entry<Pair<Pair<Integer, Integer>, Integer>, Pair<Integer, Integer>> entry : distanceMap.entrySet()) {
            //System.out.println("entry"+entry.toString());
            int nextPosX = entry.getKey().getKey().getKey();
            int nextPosY = entry.getKey().getKey().getValue();
            int direction = entry.getKey().getValue();
            int distance = entry.getValue().getKey();
            int dx = nextPosX - WAYPOINT_X; int dy = nextPosY - WAYPOINT_Y;
            int waypointDislocation = Math.abs(dx + 1) + Math.abs(dy + 1);
            if(waypointDislocation > minWaypointDislocation) continue;
            if (waypointDislocation < minWaypointDislocation || minDistance > distance) {
                //System.out.println(nextPosMinDistance.toString());
                nextPosMinDistance = entry;
                minDistance = distance;
                minWaypointDislocation = waypointDislocation;
            }
        }
        ArrayList<Integer> startToWaypoint = pathAlgorithm.getFastestPath(new RobotOrientation(map.getRobot()), new RobotOrientation(nextPosMinDistance.getKey()));
        ArrayList<Integer> waypointToFinal = pathAlgorithm.getFastestPath(new RobotOrientation(nextPosMinDistance.getKey()), new RobotOrientation(new Pair(new Pair(MAZE_HEIGHT - 4, MAZE_WIDTH - 4), 0)));
        ArrayList<Integer> finalPath = new ArrayList<Integer>();
        finalPath.addAll(startToWaypoint);
        finalPath.addAll(waypointToFinal);
        pathAlgorithm.moveRobotbyPath(finalPath, map, true);

        System.out.println("finished");
    }

    int falseSense(int posX, int posY, int direction, Cell[][] maze){
        int res = 0;
        /*if(!maze[posX-1][posY].isExplored()) res++;
        if(!maze[posX-1][posY+1].isExplored()) res++;
        if(!maze[posX-1][posY+2].isExplored()) res++;
        if(!maze[posX+3][posY].isExplored()) res++;
        if(!maze[posX+3][posY+1].isExplored()) res++;
        if(!maze[posX+3][posY+2].isExplored()) res++;
        if(!maze[posX][posY-1].isExplored()) res++;
        if(!maze[posX+1][posY-1].isExplored()) res++;
        if(!maze[posX+2][posY-1].isExplored()) res++;
        if(!maze[posX][posY+3].isExplored()) res++;
        if(!maze[posX+1][posY+3].isExplored()) res++;
        if(!maze[posX+2][posY+3].isExplored()) res++;*/
        res = new RobotOrientation(posX, posY, direction).falseSenseSensorData(map);
        return res;
    }

    public void setSpeed(int value){
        this.SPEED = value;
    }
    public void timeup(){
        this.timeout = true;
    }

    public void setCoverageThreshold(double input){
        this.coverageThreshold = input;
    }
}