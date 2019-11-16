package Hardcore;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;

public class CompassManager implements Listener{
	final HashMap<UUID, Location> bedLocs;
	final HCTweaks pl;

	public CompassManager(HCTweaks plugin){
		pl = plugin;
		bedLocs = new HashMap<UUID, Location>();
		World hardcoreWorld = pl.getServer().getWorld(pl.WORLD_NAME);
		for(String str : FileIO.loadFile("bed-locations.txt", "").split("\n")){
			int i = str.indexOf('|');
			if(i != -1){
				Location loc = EvUtils.getLocationFromString(hardcoreWorld, str.substring(i+1));
				UUID uuid = null;
				try{uuid = UUID.fromString(str.substring(0, i));}
				catch(IllegalArgumentException ex){}
				if(loc != null && uuid != null) bedLocs.put(uuid, loc);
			}
		}
	}
	void saveBedLocs(){
		FileIO.saveFile("bed-locations.txt", StringUtils.join(
				bedLocs.entrySet().stream()
				.map(entry ->
				entry.getKey().toString()+"|"+
				entry.getValue().getBlockX()+","+
				entry.getValue().getBlockY()+","+
				entry.getValue().getBlockZ())
				.iterator(), '\n'));
	}

	@EventHandler
	public void onCompassClick(PlayerInteractEvent evt){
		if(evt.getItem() == null || evt.getItem().getType() != Material.COMPASS
				|| evt.getAction() != Action.RIGHT_CLICK_AIR) return;
		if(evt.getPlayer().getScoreboardTags().contains("compass_player")){
			evt.getPlayer().removeScoreboardTag("compass_player");
			evt.getPlayer().addScoreboardTag("compass_bed");
			try{
				Location bedLoc = bedLocs.get(evt.getPlayer().getUniqueId());
				if(bedLoc != null) evt.getPlayer().setCompassTarget(bedLoc);
				evt.getPlayer().setCompassTarget(evt.getPlayer().getBedSpawnLocation());
				evt.getPlayer().setCompassTarget(evt.getPlayer().getBedLocation());
			}
			catch(NullPointerException | IllegalStateException ex){}
			evt.getPlayer().sendMessage(ChatColor.GREEN+"Compass is pointing towards your bed");
		}
		else{
			evt.getPlayer().removeScoreboardTag("compass_bed");
			evt.getPlayer().addScoreboardTag("compass_player");
			UUID closestUUID = null;
			double closestDistSq = Double.MAX_VALUE;
			for(Entry<UUID, Location> bedLoc : bedLocs.entrySet()){
				if(bedLoc.getKey().equals(evt.getPlayer().getUniqueId())) continue;
				double distSq = bedLoc.getValue().distanceSquared(evt.getPlayer().getLocation());
				if(distSq < closestDistSq){
					closestDistSq = distSq;
					closestUUID = bedLoc.getKey();
				}
			}
			if(closestUUID != null){
				Location bedLoc = bedLocs.get(closestUUID);
				evt.getPlayer().setCompassTarget(bedLoc);
				OfflinePlayer targetPlayer = pl.getServer().getOfflinePlayer(closestUUID);
				String target = targetPlayer == null ? "Unknown" : targetPlayer.getName();
				evt.getPlayer().sendMessage(ChatColor.RED+"Compass is pointing towards "+
						ChatColor.GOLD+target+ChatColor.RED+"'s bed");
				if(evt.getPlayer().hasPermission("hardcore.compass.coords")){
					evt.getPlayer().sendMessage(""+
							ChatColor.GRAY+bedLoc.getBlockX()+ChatColor.GREEN+", "+
							ChatColor.GRAY+bedLoc.getBlockY()+ChatColor.GREEN+", "+
							ChatColor.GRAY+bedLoc.getBlockZ()+ChatColor.GREEN+"."
					);
				}
			}
		}
	}

	@EventHandler
	public void onBedClick(PlayerBedEnterEvent evt){
		if(!evt.getBed().getLocation().getWorld().getName().equals(pl.WORLD_NAME)) return;
		UUID uuid = evt.getPlayer().getUniqueId();
		Location bedLoc = evt.getBed().getLocation();
		Location oldBedLoc = bedLocs.get(uuid);
		if(oldBedLoc == null || !oldBedLoc.equals(bedLoc)){
			bedLocs.put(uuid, bedLoc);
			saveBedLocs();
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent evt){
		bedLocs.remove(evt.getEntity().getUniqueId());
	}
}