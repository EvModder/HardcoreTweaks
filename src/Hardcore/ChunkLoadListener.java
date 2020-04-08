package Hardcore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ChunkLoadListener implements Listener{
	final HCTweaks pl;
	public ChunkLoadListener(HCTweaks plugin){ pl = plugin; }

	void updateRegionLog(String filename, UUID newVisitor){
		pl.getLogger().info("Writing to: "+filename+" (triggered by: "+newVisitor+")");
		StringBuilder builder = new StringBuilder();
		builder.append(System.currentTimeMillis()).append(',').append(newVisitor);
		boolean isNew = false;

		BufferedReader reader = null;
		try{reader = new BufferedReader(new FileReader(filename));}
		catch(FileNotFoundException e){
			//Create the file
			isNew = true;
			try{new File(filename).createNewFile();}
			catch(IOException e1){e1.printStackTrace();}
		}
		if(reader != null){try{
			String line;
			while((line = reader.readLine()) != null){
				if(line.endsWith(newVisitor.toString())) continue;
				builder.append('\n').append(line);
			}
			reader.close();
		}catch(IOException e){}}
		if(isNew) builder.append("created: ").append(System.currentTimeMillis());
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
			writer.write(builder.toString()); writer.close();
		}catch(IOException e){}
	}

	final class RegionUpdateEvent{
		long rX, rZ;
		UUID worldUUID, playerUUID;
		public RegionUpdateEvent(long x, long z, UUID w, UUID p){rX=x; rZ=z; worldUUID=w; playerUUID=p;}
		@Override public boolean equals(Object o){
			return o != null && o instanceof RegionUpdateEvent &&
					rX == ((RegionUpdateEvent)o).rX &&
					rZ == ((RegionUpdateEvent)o).rZ &&
					worldUUID.equals(((RegionUpdateEvent)o).worldUUID) &&
					playerUUID.equals(((RegionUpdateEvent)o).playerUUID);
		}
		@Override public int hashCode(){
			return (int)rX ^ playerUUID.hashCode() ^ (int)rZ ^ worldUUID.hashCode();
		}
	}
	final HashSet<RegionUpdateEvent> queuedUpdates = new HashSet<>();
	void queueVisitorUpdate(long rX, long rZ, UUID worldUUID, UUID playerUUID){
		final RegionUpdateEvent rXZ_Key = new RegionUpdateEvent(rX, rZ, worldUUID, playerUUID);
		if(queuedUpdates.add(rXZ_Key)){
			World world = pl.getServer().getWorld(worldUUID);
			//String rFile = "./"+world.getName()+"/region/r."+rX+"."+rZ+".mca";
			String logFile = null;
			switch(world.getEnvironment()){
				case NORMAL:
					logFile = "./"+world.getName()+"/region/r."+rX+"."+rZ+".visitlog";
					break;
				case NETHER:
					logFile = "./"+world.getName()+"/DIM-1/region/r."+rX+"."+rZ+".visitlog";
					break;
				case THE_END:
					logFile = "./"+world.getName()+"/DIM1/region/r."+rX+"."+rZ+".visitlog";
					break;
			}
			updateRegionLog(logFile, playerUUID);
			new BukkitRunnable(){@Override public void run(){queuedUpdates.remove(rXZ_Key);}}.runTaskLater(pl, 20*60*60*2);//2h
		}
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent evt){
		//new BukkitRunnable(){@Override public void run(){
			Player player = null;
			double dSqNearest = 200*200;
			Location chunkCenter = new Location(evt.getWorld(), evt.getChunk().getX()*16 + 7, 127, evt.getChunk().getZ()*16 + 7);
			for(Player p : evt.getWorld().getPlayers()){//TODO: Consider optimizing performance here?
				double dSq = p.getLocation().distanceSquared(chunkCenter);
				if(dSq < dSqNearest && !p.isDead() && p.getGameMode() == GameMode.SURVIVAL && !p.hasPermission("hardcore.novisitlog")){
					dSqNearest = dSq;
					player = p;
				}
			}
			if(player != null){
				queueVisitorUpdate(evt.getChunk().getX()/32, evt.getChunk().getZ()/32, evt.getChunk().getWorld().getUID(), player.getUniqueId());
			}
		//}}.runTaskAsynchronously(pl);
	}
}