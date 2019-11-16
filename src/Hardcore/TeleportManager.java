package Hardcore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitRunnable;
import Hardcore.SpectatorManager.WatchMode;
import Hardcore.commands.CommandTpa;
import Hardcore.commands.CommandTpaccept;
import Hardcore.commands.CommandTpahere;
import net.evmodder.EvLib.util.Pair;

public class TeleportManager implements Listener{
	final HCTweaks pl;
	final HashMap<UUID, HashSet<UUID>> pendingTpas, pendingTpaheres;//to -> from
	final HashMap<Pair<UUID, UUID>, BukkitRunnable> tpTimeouts;

	public TeleportManager(HCTweaks plugin){
		pl = plugin;
		pendingTpas = new HashMap<UUID, HashSet<UUID>>();
		pendingTpaheres = new HashMap<UUID, HashSet<UUID>>();
		tpTimeouts = new HashMap<Pair<UUID, UUID>, BukkitRunnable>();
		new CommandTpaccept(pl, this);
		new CommandTpa(pl, this);
		new CommandTpahere(pl, this);
	}

	public static String name_from_tp_tag(String tag){
		try{
			OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(UUID.fromString(tag.substring(3)));
			return player.getName();
		}
		catch(NullPointerException | IllegalArgumentException ex){return null;}
	}
	public static List<String> get_tp_tags(Player p1){
		return p1.getScoreboardTags().stream().filter(tag -> tag.startsWith("tp_")).collect(Collectors.toList());
	}
	public static boolean check_tp_tags(Player p1, Player p2){
		return p1.getScoreboardTags().contains("tp_"+p2.getUniqueId()) ||
				p2.getScoreboardTags().contains("tp_"+p1.getUniqueId());
	}
	static void add_tp_tags(Player p1, Player p2){
		HCTweaks.getPlugin().getLogger().info("Tagging "+p1.getName()+"<=>"+p2.getName());
		p1.sendMessage(ChatColor.GRAY+"You will no longer be able to tp "+
				ChatColor.WHITE+p2.getName()+ChatColor.GRAY+" (in this life).");
		p2.sendMessage(ChatColor.GRAY+"You will no longer be able to tp "+
				ChatColor.WHITE+p1.getName()+ChatColor.GRAY+" (in this life).");

		List<String> p1tps = p1.getScoreboardTags().stream()
				.filter(tag -> tag.startsWith("tp_") && !p2.getScoreboardTags().contains(tag))
				.collect(Collectors.toList());
		List<String> p2tps = p2.getScoreboardTags().stream()
				.filter(tag -> tag.startsWith("tp_") && !p1.getScoreboardTags().contains(tag))
				.collect(Collectors.toList());
		if(!p1tps.isEmpty()){
			p2.getScoreboardTags().addAll(p1tps);
			p2.sendMessage(ChatColor.GRAY+"Due to "+ChatColor.GREEN+p1.getDisplayName()+
					ChatColor.GRAY+"'s tp-history, you can also no longer tp: \n"+
					p1tps.stream()
					.map(tag -> ChatColor.GOLD+name_from_tp_tag(tag))
					.sorted().collect(Collectors.joining(ChatColor.GRAY+", "))
					+".");
		}
		if(!p2tps.isEmpty()){
			p1.getScoreboardTags().addAll(p2tps);
			p1.sendMessage(ChatColor.GRAY+"Due to "+ChatColor.GREEN+p2.getDisplayName()+
					ChatColor.GRAY+"'s past teleports, you can also no longer tp: \n"+
					p2tps.stream()
					.map(tag -> ChatColor.GOLD+name_from_tp_tag(tag))
					.sorted().collect(Collectors.joining(ChatColor.GRAY+", "))
					+".");
		}
		p1.addScoreboardTag("tp_"+p2.getUniqueId());
		p2.addScoreboardTag("tp_"+p1.getUniqueId());
		WatchMode p1Mode = SpectatorManager.getSpectateMode(p1);
		WatchMode p2Mode = SpectatorManager.getSpectateMode(p2);
		if(p1Mode != SpectatorManager.DEFAULT_MODE && SpectatorManager.isDefaultSpectateMode(p2))
			SpectatorManager.setSpectateMode(p2, p1Mode);
		if(p2Mode != SpectatorManager.DEFAULT_MODE && SpectatorManager.isDefaultSpectateMode(p1))
			SpectatorManager.setSpectateMode(p1, p2Mode);
	}

	public List<String> get_pending_tps(UUID target){
		HashSet<UUID> requesters = new HashSet<UUID>();
		requesters.addAll(pendingTpas.getOrDefault(target, new HashSet<UUID>()));
		requesters.addAll(pendingTpaheres.getOrDefault(target, new HashSet<UUID>()));
		return requesters.stream()
				.map((uuid) -> {
					OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
					return player == null ? null : player.getName();
				})
				.filter(x -> x != null).sorted().collect(Collectors.toList());
	}

	private void cancelTimeout(Pair<UUID, UUID> tpPair){
		BukkitRunnable timeout = tpTimeouts.remove(tpPair);
		if(timeout != null && !timeout.isCancelled()) timeout.cancel();
	}

	public void addPendingTpa(Player from, Player target){
		if(check_tp_tags(from, target)){
			from.sendMessage(ChatColor.RED+"You already have a teleport directly or indirectly connected to "
					+target.getDisplayName());
			return;
		}
		final UUID fromUUID = from.getUniqueId(), targetUUID = target.getUniqueId();
		HashSet<UUID> tpasToTarget = pendingTpas.getOrDefault(targetUUID, new HashSet<UUID>());
		if(!tpasToTarget.add(fromUUID)){
			from.sendMessage(ChatColor.RED+"You already have a pending tpa to "+ChatColor.GREEN+target.getDisplayName());
			return;
		}
		if(pendingTpaheres.getOrDefault(targetUUID, new HashSet<UUID>()).contains(fromUUID)){
			from.sendMessage(ChatColor.RED+"You cannot send a tpa to "+
					ChatColor.GREEN+target.getDisplayName()+" until your tpahere request expires");
			return;
		}
		target.sendMessage(ChatColor.GREEN+from.getDisplayName()+ChatColor.LIGHT_PURPLE+" requests to teleport to you");
		target.sendMessage(ChatColor.LIGHT_PURPLE+"To accept, type "+ChatColor.AQUA+"/tpaccept "+from.getName());
		target.sendMessage(ChatColor.GRAY+"If you accept, you will no longer be able to teleport: \n"+
				get_tp_tags(from).stream()
				.map(tag -> ChatColor.GOLD+name_from_tp_tag(tag))
				.collect(Collectors.joining(ChatColor.GRAY+", ")));
		from.sendMessage(ChatColor.LIGHT_PURPLE+"Sent a tpa request to "+ChatColor.GREEN+target.getDisplayName());

		BukkitRunnable timeout = new BukkitRunnable(){@Override public void run(){
			Player from = pl.getServer().getPlayer(fromUUID), target = pl.getServer().getPlayer(targetUUID);
			if(from != null) from.sendMessage(ChatColor.RED+"Your tpa request to "+
					ChatColor.GREEN+target.getDisplayName()+ChatColor.RED+" has expired");
			if(target != null) target.sendMessage(ChatColor.RED+"The tpa request from "+
					ChatColor.GREEN+from.getCustomName()+ChatColor.RED+" has expired");
			tpTimeouts.remove(new Pair<UUID, UUID>(fromUUID, targetUUID));
		}};
		tpTimeouts.put(new Pair<UUID, UUID>(fromUUID, targetUUID), timeout);
		timeout.runTaskLater(pl, 2*60*20);
	}

	public void addPendingTpahere(Player from, Player target){
		if(check_tp_tags(from, target)){
			from.sendMessage(ChatColor.RED+"You already have a teleport directly or indirectly connected to "
					+target.getDisplayName());
			return;
		}
		final UUID fromUUID = from.getUniqueId(), targetUUID = target.getUniqueId();
		HashSet<UUID> tpaheresToTarget = pendingTpaheres.getOrDefault(targetUUID, new HashSet<UUID>());
		if(!tpaheresToTarget.add(fromUUID)){
			from.sendMessage(ChatColor.RED+"You already have a pending tpahere for "+ChatColor.GREEN+target.getDisplayName());
			return;
		}
		if(pendingTpas.getOrDefault(targetUUID, new HashSet<UUID>()).contains(fromUUID)){
			from.sendMessage(ChatColor.RED+"You cannot send a tpahere to "+
					ChatColor.GREEN+target.getDisplayName()+" until your tpa request expires");
			return;
		}
		target.sendMessage(ChatColor.GREEN+from.getDisplayName()+ChatColor.LIGHT_PURPLE+" requests that you teleport to them");
		target.sendMessage(ChatColor.LIGHT_PURPLE+"To accept, type "+ChatColor.AQUA+"/tpaccept "+from.getName());
		target.sendMessage(ChatColor.GRAY+"If you accept, you will no longer be able to teleport: \n"+
				get_tp_tags(from).stream()
				.map(tag -> ChatColor.GOLD+name_from_tp_tag(tag))
				.collect(Collectors.joining(ChatColor.GRAY+", ")));
		from.sendMessage(ChatColor.LIGHT_PURPLE+"Sent a tpahere request to "+ChatColor.GREEN+target.getDisplayName());

		BukkitRunnable timeout = new BukkitRunnable(){@Override public void run(){
			Player from = pl.getServer().getPlayer(fromUUID), target = pl.getServer().getPlayer(targetUUID);
			if(from != null) from.sendMessage(ChatColor.RED+"Your tpahere request to "+
					ChatColor.GREEN+target.getDisplayName()+ChatColor.RED+" has expired");
			if(target != null) target.sendMessage(ChatColor.RED+"The tpahere request from "+
					ChatColor.GREEN+from.getCustomName()+ChatColor.RED+" has expired");
			tpTimeouts.remove(new Pair<UUID, UUID>(fromUUID, targetUUID));
		}};
		tpTimeouts.put(new Pair<UUID, UUID>(fromUUID, targetUUID), timeout);
		timeout.runTaskLater(pl, 2*60*20);
	}

	public void acceptTeleport(Player accepter, Player from){
		pl.getLogger().info(accepter.getName()+" accepted "+from.getName()+"'s tp request");

		final UUID fromUUID = from.getUniqueId(), accepterUUID = accepter.getUniqueId();
		boolean tpa = pendingTpas.getOrDefault(accepterUUID, new HashSet<UUID>()).remove(fromUUID);
		boolean tpahere = pendingTpaheres.getOrDefault(accepterUUID, new HashSet<UUID>()).remove(fromUUID);
		if(!tpa && !tpahere){
			accepter.sendMessage(ChatColor.RED+"You do not have a pending teleport from "+ChatColor.GREEN+from.getDisplayName());
			return;
		}
		cancelTimeout(new Pair<UUID, UUID>(fromUUID, accepterUUID));
		if(check_tp_tags(from, accepter)){
			accepter.sendMessage(ChatColor.RED+"You already have a teleport connected to "+from.getDisplayName());
			from.sendMessage(ChatColor.RED+"You already have a teleport connected to "+accepter.getDisplayName());
			return;
		}
		from.sendMessage(ChatColor.GREEN+accepter.getDisplayName()+
				ChatColor.LIGHT_PURPLE+" accepted your "+(tpa ? "/tpa" : "/tpahere"));
		accepter.sendMessage(ChatColor.LIGHT_PURPLE+"Accepted "+ChatColor.GREEN+from.getDisplayName()+
				ChatColor.LIGHT_PURPLE+"'s "+(tpa ? "/tpa" : "/tpahere"));
		add_tp_tags(accepter, from);
		if(tpa) from.teleport(accepter, TeleportCause.CHORUS_FRUIT);
		if(tpahere) accepter.teleport(from, TeleportCause.CHORUS_FRUIT);
	}


	/*static Player getNearbyGm0WithPermsInRadius(Location loc, Player spec, double r){
		double closestDistGm0 = r*r; // distanceSquared
		Player closestPlayer = null;
		for(Player p : loc.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL && (dist=p.getLocation().distanceSquared(loc)) < closestDistGm0
					&& (!SpectatorManager.isSpectatorFavorYes(spec) || SpectatorManager.canSpectate(spec.getUniqueId(), p))){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		return closestPlayer;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onTeleport(PlayerTeleportEvent evt){
		pl.getLogger().info(evt.getPlayer().getName()+" TeleportCause: "+evt.getCause());
		if(evt.isCancelled() || evt.getPlayer().getScoreboardTags().contains("unconfirmed")) return;
		switch(evt.getCause()){
			//Unknown: nether portal = two teleport events:
			//one for dimension shift, and one for y axis. Second event is "UNKNOWN"
			case COMMAND: case SPECTATE: case PLUGIN: break;
			case CHORUS_FRUIT: return;
			default:
				if(evt.getPlayer().getGameMode() == GameMode.SURVIVAL) return;
		}
		Player teleporter = evt.getPlayer();
		Player receiver = getNearbyGm0WithPermsInRadius(evt.getTo(), teleporter, 100D);
		if(receiver == null){
			teleporter.sendMessage(ChatColor.RED+"Unable to locate destination player");
			//pl.getLogger().warning("Teleport failed: receiver == null,"
			//		+ "tpToLoc: "+evt.getTo().getBlockX()+","+evt.getTo().getBlockY()+","+evt.getTo().getBlockZ());
			if(teleporter.hasPermission("hardcore.teleport.override")) return;
			evt.setCancelled(true);
		}
		else{
			if(teleporter.hasPermission("hardcore.teleport.override")) return;
			if(teleporter.getGameMode() != GameMode.SURVIVAL){
				pl.getLogger().info("GameMode != SURVIVAL; tp permitted");
				if(teleporter.getGameMode() == GameMode.SPECTATOR) teleporter.setSpectatorTarget(receiver);
				return;
			}
			teleporter.sendMessage(ChatColor.RED+"Error: Could not find a pending tpa with "+receiver.getName());
			pl.getLogger().warning("Unable to find a tpa from "+teleporter.getName()+" to "+receiver.getName());
			evt.setCancelled(true);
		}
	}*/
}