package Hardcore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import Hardcore.SpectatorManager.WatchMode;

public class TeleportManager implements Listener{
	final HCTweaks pl;
	final HashSet<String> tpaAliases, tpahereAliases, tpacceptAliases;
	final HashMap<UUID, UUID> pendingTpas, pendingTpaheres;//from -> to

	public TeleportManager(HCTweaks plugin){
		pl = plugin;
		PluginCommand cmdTpa = pl.getServer().getPluginCommand("tpa");
		PluginCommand cmdTpahere = pl.getServer().getPluginCommand("tpahere");
		PluginCommand cmdTpaccept = pl.getServer().getPluginCommand("tpaccept");
		if(cmdTpa == null || cmdTpa.getAliases() == null || cmdTpa.getLabel() == null)
			pl.getLogger().warning("Could not find command: /tpa");
		if(cmdTpahere == null) pl.getLogger().warning("Could not find command: /tpahere");
		if(cmdTpaccept == null) pl.getLogger().warning("Could not find command: /tpaccept");
		tpaAliases = new HashSet<String>();
		tpahereAliases = new HashSet<String>();
		tpacceptAliases = new HashSet<String>();
		tpaAliases.addAll(cmdTpa.getAliases()); tpaAliases.add(cmdTpa.getLabel());
		tpahereAliases.addAll(cmdTpahere.getAliases()); tpahereAliases.add(cmdTpahere.getLabel());
		tpacceptAliases.addAll(cmdTpaccept.getAliases()); tpacceptAliases.add(cmdTpaccept.getLabel());
		pl.getLogger().fine("Tpa aliases: " + tpaAliases.toString());
		pl.getLogger().fine("Tpahere aliases: " + tpahereAliases.toString());
		pl.getLogger().fine("Tpaccept aliases: " + tpacceptAliases.toString());
		pendingTpas = new HashMap<UUID, UUID>();
		pendingTpaheres = new HashMap<UUID, UUID>();
	}

	static void add_tp_tags(Player p1, Player p2){
		HCTweaks.getPlugin().getLogger().info("Tagging "+p1.getName()+"<=>"+p2.getName());
		p1.sendMessage(ChatColor.GRAY+"You will no longer be able to tp "+
				ChatColor.WHITE+p2.getName()+ChatColor.GRAY+" (in this life).");
		p2.sendMessage(ChatColor.GRAY+"You will no longer be able to tp "+
				ChatColor.WHITE+p1.getName()+ChatColor.GRAY+" (in this life).");

		TreeSet<String> p1tps = new TreeSet<String>(), p2tps = new TreeSet<String>();
		for(String tag : p1.getScoreboardTags())
			if(tag.startsWith("tp_") && !p2.getScoreboardTags().contains(tag)
					&& !("tp_"+p2.getUniqueId()).equals(tag)) p1tps.add(tag);
		for(String tag : p2.getScoreboardTags())
			if(tag.startsWith("tp_") && !p1.getScoreboardTags().contains(tag)
					&& !("tp_"+p1.getUniqueId()).equals(tag)) p2tps.add(tag);
		if(!p1tps.isEmpty()){
			p2.sendMessage(ChatColor.GRAY+"Due to "+
					ChatColor.WHITE+p1.getName()+ChatColor.GRAY+"'s tp-history, you can also no longer tp:");
			StringBuilder noTps = new StringBuilder("");
			for(String tag : p1tps){
				try{
					UUID uuid = UUID.fromString(tag.substring(3));
					OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
					if(player != null) noTps.append(ChatColor.WHITE).append(player.getName())
						.append(ChatColor.GRAY).append(", ");
				}
				catch(IllegalArgumentException ex){continue;}
				p2.addScoreboardTag(tag);
			}
			p2.sendMessage(noTps.toString().substring(0, noTps.length()-2)+".");
		}
		if(!p2tps.isEmpty()){
			p1.sendMessage(ChatColor.GRAY+"Due to "+
					ChatColor.WHITE+p2.getName()+ChatColor.GRAY+"'s past teleports, you can no longer tp:");
			StringBuilder noTps = new StringBuilder("");
			for(String tag : p2tps){
				try{
					UUID uuid = UUID.fromString(tag.substring(3));
					OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
					if(player != null) noTps.append(ChatColor.WHITE).append(player.getName())
						.append(ChatColor.GRAY).append(", ");
				}
				catch(IllegalArgumentException ex){continue;}
				p1.addScoreboardTag(tag);
			}
			p1.sendMessage(noTps.toString().substring(0, noTps.length()-2)+".");
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
	static boolean check_tp_tags(Player p1, Player p2){
		return p1.getScoreboardTags().contains("tp_"+p2.getUniqueId()) ||
				p2.getScoreboardTags().contains("tp_"+p1.getUniqueId());
	}
	public static List<String> get_tp_tags(Player p1){
		return p1.getScoreboardTags().stream().filter(tag -> tag.startsWith("tp_")).collect(Collectors.toList());
	}
	public static String getNameFromTpTag(String tag){
		try{
			OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(UUID.fromString(tag.substring(3)));
			return player.getName();
		}
		catch(NullPointerException | IllegalArgumentException ex){return null;}
	}

	public void notifyTpTarget(Player target, Player sender){
		target.sendMessage(ChatColor.GREEN+sender.getName()+" has sent you a teleport request");
		target.sendMessage(ChatColor.GRAY+"To accept it, type "
						+ChatColor.DARK_GREEN+"/tpaccept "+sender.getName());
		StringBuilder builder = new StringBuilder("").append(ChatColor.GRAY).append(
				"If you accept it, you will no longer be able to teleport the following players: ");
		for(String tag : get_tp_tags(sender)){
			if(!target.getScoreboardTags().contains(tag)){
				builder.append(ChatColor.GOLD).append(getNameFromTpTag(tag)).append(ChatColor.GRAY).append(", ");
			}
		}
		target.sendMessage(builder.substring(0, builder.length()-2)+".");
	}

	@SuppressWarnings("deprecation") @EventHandler
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().charAt(0) != '/') return;
		String message = evt.getMessage().trim();
		String command = message.toLowerCase();
		int space = command.indexOf(' ');
		command = (space > 0 ? command.substring(1, space) : command.substring(1));
		Player player = evt.getPlayer();

		if(command.equals("tp")) {
			if(SpectatorManager.isSpectator(player)){
				evt.setCancelled(true);
				Player target = null;
				if(space < 0 || (target=pl.getServer().getPlayer(message.substring(space + 1))) == null){
					player.sendMessage(ChatColor.RED+"Please specify who you wish to tp to (exact username)");
					player.sendMessage("Note: you can also use vanilla spectator menu (press 1)");
				}
				else{
					player.teleport(target, TeleportCause.COMMAND);
					player.setSpectatorTarget(target);
				}
			}
		}
		else if(tpaAliases.contains(command)){
			if(SpectatorManager.isSpectator(player)){
				player.sendMessage(ChatColor.RED+"Only the living may use this command");
				evt.setCancelled(true);
				return;
			}
			Player target = pl.getServer().getPlayer(message.substring(space + 1).trim());
			if(space < 0 || target == null){
				player.sendMessage(ChatColor.RED + "Please specify who to tpa to " + ChatColor.UNDERLINE + "exactly");
				evt.setCancelled(true);
			}
			else if(target.getUniqueId().equals(player.getUniqueId())){
				player.sendMessage(ChatColor.RED + "No need to teleport to yourself =P");
				evt.setCancelled(true);
				return;
			}
			else if(check_tp_tags(player, target)){
				player.sendMessage(ChatColor.RED +
						"You have already used a tp that is connected to " + target.getName());
				evt.setCancelled(true);
				return;
			}
			else{
				player.sendMessage(ChatColor.LIGHT_PURPLE + "Sent a tpa to " + target.getName());
				notifyTpTarget(target, player);
				pendingTpas.put(player.getUniqueId(), target.getUniqueId());
			}
		}
		else if(tpahereAliases.contains(command)){
			if(SpectatorManager.isSpectator(player)){
				player.sendMessage(ChatColor.RED+"Only the living may use this command");
				evt.setCancelled(true);
				return;
			}
			Player target = pl.getServer().getPlayer(message.substring(space + 1).trim());
			if(space < 0 || target == null){
				player.sendMessage(ChatColor.RED + "Please specify who to tpahere " + ChatColor.UNDERLINE + "exactly");
				evt.setCancelled(true);
				return;
			}
			else if(target.getUniqueId().equals(player.getUniqueId())){
				player.sendMessage(ChatColor.RED + "No need to teleport yourself =P");
				evt.setCancelled(true);
				return;
			}
			else if(check_tp_tags(player, target)){
				player.sendMessage(ChatColor.RED + "You have already used a tp connected to " + target.getName());
				evt.setCancelled(true);
				return;
			}
			else{
				player.sendMessage(ChatColor.LIGHT_PURPLE + "Sent a tpahere to " + target.getName());
				notifyTpTarget(target, player);
				pendingTpaheres.put(player.getUniqueId(), target.getUniqueId());
			}
		}
		else if(tpacceptAliases.contains(command)){
			if(SpectatorManager.isSpectator(player)){
				player.sendMessage(ChatColor.RED+"Only the living may use this command");
				evt.setCancelled(true);
				return;
			}
			String targetName = message.substring(space + 1).trim();
			Player target = pl.getServer().getPlayer(targetName);
			if(space < 0 || target == null){
				player.sendMessage(ChatColor.RED + "Please specify the player whose request you are accepting");
				player.sendMessage(ChatColor.GRAY+"/tpaccept <name>");
				evt.setCancelled(true);
				return;
			}
			else if(pendingTpaheres.containsKey(target.getUniqueId())){
				player.sendMessage(ChatColor.LIGHT_PURPLE + "Accepting " + targetName + "'s tpahere request");
			}
			else if(pendingTpas.containsKey(target.getUniqueId())){
				player.sendMessage(ChatColor.LIGHT_PURPLE + "Accepting " + targetName + "'s tpa request");
			}
			else {}
		}
	}

	static Player getNearbyGm0WithPerms(Location loc, Player spec){
		double closestDistGm0 = 10000D;
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
		if(evt.isCancelled() || evt.getPlayer().getScoreboardTags().contains("unconfirmed")) return;
		pl.getLogger().info(evt.getPlayer().getName()+" teleport cause: "+evt.getCause());
		switch(evt.getCause()){
			//Unknown: nether portal = two teleport events:
			//one for dimension shift, and one for y axis. Second event is "UNKNOWN"
			case COMMAND: case SPECTATE: case PLUGIN: break;
			case CHORUS_FRUIT: return;
			default:
				if(evt.getPlayer().getGameMode() == GameMode.SURVIVAL) return;
		}
		Player teleporter = evt.getPlayer();
		Player receiver = getNearbyGm0WithPerms(evt.getTo(), teleporter);
		if(receiver == null){
			teleporter.sendMessage(ChatColor.RED+"Unable to locate destination player");
			pl.getLogger().warning("Teleport failed: receiver == null");
			if(teleporter.hasPermission("hardcore.teleport.override")) return;
			evt.setCancelled(true);
		}
		else if(pendingTpas.getOrDefault(teleporter.getUniqueId(), UUID.randomUUID()).equals(receiver.getUniqueId())){
			pl.getLogger().info(receiver.getName()+" accepted "+teleporter.getName()+"'s /tpa");
			pendingTpas.remove(teleporter.getUniqueId());
			teleporter.sendMessage(ChatColor.GREEN+receiver.getName()+" accepted your tpa");
			receiver.sendMessage(ChatColor.GREEN+"Accepted "+teleporter.getName()+"'s tpa");
//			if(!teleporter.isOp()) pl.setPermission(teleporter, "essentials.tpa", false);
//			if(!receiver.isOp()) pl.setPermission(receiver, "essentials.tpaccept", false);
			add_tp_tags(teleporter, receiver);
//			teleporter.removeScoreboardTag("has_tpa");
//			receiver.removeScoreboardTag("has_tpaccept");
		}
		else if(pendingTpaheres.getOrDefault(receiver.getUniqueId(), UUID.randomUUID()).equals(teleporter.getUniqueId())){
			pendingTpaheres.remove(receiver.getUniqueId());
			pl.getLogger().info(teleporter.getName()+" accepted "+receiver.getName()+"'s /tpahere");
			receiver.sendMessage(ChatColor.GREEN+teleporter.getName()+" accepted your tpa");
			teleporter.sendMessage(ChatColor.GREEN+"Accepted "+receiver.getName()+"'s tpa");
//			if(!receiver.isOp()) pl.setPermission(receiver, "essentials.tpahere", false);
//			if(!teleporter.isOp()) pl.setPermission(teleporter, "essentials.tpaccept", false);
			add_tp_tags(receiver, teleporter);
//			receiver.removeScoreboardTag("has_tpahere");
//			teleporter.removeScoreboardTag("has_tpaccept");
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
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent evt){
		pendingTpas.remove(evt.getPlayer().getUniqueId());
		pendingTpaheres.remove(evt.getPlayer().getUniqueId());
	}
}