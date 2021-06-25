/*
 * Curved Stroke Schematization
 * Copyright (C) 2021   
 * Developed by 
 *   Arthur van Goethem (a.i.v.goethem@tue.nl) 
 *   Wouter Meulemans (w.meulemans@tue.nl)
 * 
 * Licensed under GNU GPL v3. See provided LICENSE document for more information.
 */
package nl.tue.curvedstrokeschematization.data.metro;

import java.awt.Color;
import java.util.ArrayList;

/**
 * @author Arthur van Goethem (a.i.v.goethem@tue.nl)
 */
public class MetroLine
{
    String label;
    Color color;
    ArrayList<MetroConnection> connections;
    
    public MetroLine(String label, Color color)
    {
        this.label = label;
        this.color = color;
        connections = new ArrayList<MetroConnection>();
    }
    
    public String getLabel()
    {
        return label;
    }
    
    public Color getColor()
    {
        return color;
    }
    
    public ArrayList<MetroConnection> getConnections()
    {
        return connections;
    }
    
    public ArrayList<MetroStation> getStations()
    {
        ArrayList<MetroStation> stations = new ArrayList<MetroStation>();
        if (connections.size() == 1)
        {
            stations.add(connections.get(0).getBeginStation());
            stations.add(connections.get(0).getEndStation());
        }
        else if (connections.size() > 1)
        {
            MetroStation lastStation = connections.get(0).getSharedStation(connections.get(1));
            stations.add(connections.get(0).theOther(lastStation));
            stations.add(lastStation);
            for (int i = 1; i < connections.size(); i++)
            {
                MetroStation ms = (MetroStation)connections.get(i).theOther(lastStation);
                stations.add(ms);
                lastStation = ms;
            }
        }    
        return stations;
    }
    
    public void addConnection(MetroConnection mc)
    {
        connections.add(mc);
    }
    
    //cant fully sort => some lines split in multiple parts
    public void sortConnections()
    {
        if (connections.size() == 0)
            return;
         
        //O(n^2)
        ArrayList<MetroConnection> sortedConnections     = new ArrayList<MetroConnection>();
        ArrayList<MetroConnection> fullSortedConnections = new ArrayList<MetroConnection>();
        
        sortedConnections.add(connections.get(0));
        connections.remove(0);
        MetroStation startStation = sortedConnections.get(0).getBeginStation();
        MetroStation endStation = sortedConnections.get(0).getEndStation();
        while (connections.size() > 0)
        {
            int sizeBefore = connections.size();
            for (int i = 0; i < connections.size(); i++)
            {
                if (connections.get(i).getBeginStation() == startStation || connections.get(i).getEndStation() == startStation)
                {
                    sortedConnections.add(0, connections.get(i));
                    startStation = connections.get(i).theOther(startStation);
                    connections.remove(i);                
                    i = -1;
                }
                else if (connections.get(i).getBeginStation() == endStation || connections.get(i).getEndStation() == endStation)
                {
                    sortedConnections.add(connections.get(i));
                    endStation = connections.get(i).theOther(endStation);
                    connections.remove(i);                
                    i = -1;                
                }
            }
            int sizeAfter = connections.size();
            
            if (sizeBefore == sizeAfter)
            {
                fullSortedConnections.addAll(sortedConnections);
                sortedConnections.clear();
                sortedConnections.add(connections.get(0));
                connections.remove(0);
                startStation = sortedConnections.get(0).getBeginStation();
                endStation   = sortedConnections.get(0).getEndStation();
            }
        }
        fullSortedConnections.addAll(sortedConnections);
        connections = fullSortedConnections;
        
        if (connections.size() > 1)
        {
            MetroStation curStation = connections.get(0).theOther(connections.get(0).getSharedStation(connections.get(1)));
            for (int i = 0; i < connections.size(); i++)
            {
                if (connections.get(i).getEndStation() == curStation)
                {
                    connections.get(i).setEndStation(connections.get(i).getBeginStation());
                    connections.get(i).setBeginStation(curStation);
                }
                curStation = connections.get(i).getEndStation();
            }
        }
    }
    
    public String outputLine()
    {
        ArrayList<MetroStation> stations = getStations();
        if (stations.size() == 0)
            return "";
        
        String s = stations.get(0).toString();
        
        for (int i = 1; i < stations.size(); i++)
        {
            s += " <-> " + stations.get(i);
        }
        return s;
    }
    
    public String outputLine2()
    {
        String s = "";
        for (int i = 0; i < connections.size(); i++)
        {
            s += connections.toString();
        }
        
        return s;
    }    
    
    @Override
    public String toString()
    {
        return label;
    }
}
