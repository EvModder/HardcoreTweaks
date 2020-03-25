package Hardcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import Hardcore.HCTweaks;
import Hardcore.SpectatorManager;
import Hardcore.SpectatorManager.WatchMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.evmodder.EvLib.EvCommand;

public class CommandSpectate extends EvCommand{
	final HCTweaks pl;

	public CommandSpectate(HCTweaks plugin){
		super(plugin);
		pl = plugin;
	}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		if(s instanceof Player == false) return null;
		if(args.length == 1){
			ArrayList<String> tabCompletes = new ArrayList<String>();
			if("blacklist".startsWith(args[0])) tabCompletes.add("blacklist");
			if("whitelist".startsWith(args[0])) tabCompletes.add("whitelist");
			if("off".startsWith(args[0])) tabCompletes.add("whitelist");
			if("block-all".startsWith(args[0])) tabCompletes.add("whitelist");
			if("disable".startsWith(args[0])) tabCompletes.add("whitelist");
			if("mode".startsWith(args[0])) tabCompletes.add("mode");
			if("list".startsWith(args[0]) && !SpectatorManager.isSpectator((Player)s)) tabCompletes.add("list");
			return tabCompletes.isEmpty() ? null : tabCompletes;
		}
		else if(args.length == 2){
			args[1] = args[1].toLowerCase();
			if(args[0].equals("mode")) return Arrays.asList("blacklist", "whitelist");
			else if(args[0].equals("blacklist") || args[0].equals("whitelist"))
					return pl.getServer().getOnlinePlayers()
							.stream().map(p -> p.getName())
							.filter(name -> name.toLowerCase().startsWith(args[1]))
							.collect(Collectors.toList());
		}
		return null;
	}

	static String getNameFromListTag(String tag){
		try{//spectator_whitelist_
			OfflinePlayer player = org.bukkit.Bukkit.getServer().getOfflinePlayer(UUID.fromString(tag.substring(20)));
			return player.getName();
		}
		catch(NullPointerException | IllegalArgumentException ex){return null;}
	}

	static void displayWhitelist(CommandSender requester, Player target){
		StringBuilder builder = new StringBuilder("").append(ChatColor.GRAY).append("Current whitelist: ");
		boolean empty = true;
		for(String tag : target.getScoreboardTags()){
			if(tag.startsWith("spectator_whitelist_")){
				String name = getNameFromListTag(tag);
				builder.append(ChatColor.AQUA).append(name).append(ChatColor.GRAY).append(", ");
				empty = false;
			}
		}
		if(empty) requester.sendMessage(builder.append(ChatColor.WHITE).append("Empty").toString());
		else requester.sendMessage(builder.substring(0, builder.length()-2));
	}
	static void displayBlacklist(CommandSender requester, Player target){
		StringBuilder builder = new StringBuilder("").append(ChatColor.GRAY).append("Current blacklist: ");
		boolean empty = true;
		for(String tag : target.getScoreboardTags()){
			if(tag.startsWith("spectator_blacklist_")){
				String name = getNameFromListTag(tag);
				builder.append(ChatColor.RED).append(name).append(ChatColor.GRAY).append(", ");
				empty = false;
			}
		}
		if(empty) requester.sendMessage(builder.append(ChatColor.WHITE).append("Empty").toString());
		else requester.sendMessage(builder.substring(0, builder.length()-2));
	}
	static void displayCurrentMode(Player player){
		player.sendMessage(""+ChatColor.GRAY+ChatColor.BOLD+"Current mode: "
					+ChatColor.AQUA+ChatColor.BOLD+SpectatorManager.getSpectateMode(player)+"\n"
					+ChatColor.GRAY+"Available modes: ");
		player.sendMessage(ChatColor.WHITE+"WHITELIST"
					+ChatColor.GRAY+" - List the only people who CAN spectate you\n"
		//					+ChatColor.GRAY+"(Blocks everyone by default)"
					);
		player.sendMessage(ChatColor.WHITE+"BLACKLIST"
					+ChatColor.GRAY+" - List the only people who CANNOT spectate you\n"
		//					+ChatColor.GRAY+"(Blocks nobody by default"
					);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}
		Player player = (Player) sender;
		if(args.length > 2){
			sender.sendMessage(ChatColor.RED+"Too many arguments");
			return true;
		}
		if(args.length == 0 || (args[0]=args[0].toLowerCase().replaceAll("-", "")).equals("list")){
			if(!SpectatorManager.isSpectator(player)){
				ArrayList<String> specs = new ArrayList<String>();
				for(Player p : player.getWorld().getPlayers()){
					if(SpectatorManager.isSpectator(p) && p.getLocation().distanceSquared(player.getLocation()) < 60*60){
						specs.add(p.getDisplayName());
					}
				}
				if(specs.isEmpty()) sender.sendMessage(ChatColor.GRAY+"No nearby spectators detected.");
				else if(specs.size() == 1) sender.sendMessage(
						ChatColor.GRAY+"You are currently being spectated by "+ChatColor.WHITE+specs.get(0));
				else{
					Collections.sort(specs);
					sender.sendMessage(ChatColor.GRAY+"You are currently being spectated by "
							+ChatColor.AQUA+specs.size()+ChatColor.GRAY+" players:");
					sender.sendMessage(String.join(ChatColor.GRAY+", "+ChatColor.WHITE, specs)+ChatColor.GRAY+".");
				}
			}
			if(SpectatorManager.getSpectateMode(player) == WatchMode.WHITELIST) displayWhitelist(player, player);
			else displayBlacklist(player, player);
			return true;
		}
		if(args.length == 1){
			if(args[0].equals("whitelist") || args[0].equals("wl")
					|| args[0].equals("blockall") || args[0].equals("disable") || args[0].equals("off")){
				if(SpectatorManager.getSpectateMode(player) == WatchMode.WHITELIST) displayWhitelist(player, player);
				else{
					SpectatorManager.setSpectateMode(player, WatchMode.WHITELIST);
					displayCurrentMode(player);
				}
			}
			else if(args[0].equals("blacklist") || args[0].equals("bl") || args[0].equals("blocked")){
				if(SpectatorManager.getSpectateMode(player) == WatchMode.BLACKLIST) displayBlacklist(player, player);
				else{
					SpectatorManager.setSpectateMode(player, WatchMode.BLACKLIST);
					displayCurrentMode(player);
				}
			}
			else if(args[0].equals("mode")) displayCurrentMode(player);
			else{
				Player target = pl.getServer().getPlayer(args[0]);
				if(target != null && SpectatorManager.isSpectator(player)
						&& SpectatorManager.canSpectate(player.getUniqueId(), target)){
					sender.sendMessage("Command interpreted as /tp "+target.getName());
					if(player.teleport(target, TeleportCause.COMMAND)){
						player.setSpectatorTarget(target);
					}
				}
				else sender.sendMessage("Unknown argument \""+args[0]+"\"");
			}
		}
		else{
			args[1] = args[1].toLowerCase();
			if(args[0].equals("mode")){
				if(args[1].equals("whitelist"))
					SpectatorManager.setSpectateMode(player, WatchMode.WHITELIST);
				else if(args[1].equals("blacklist"))
					SpectatorManager.setSpectateMode(player, WatchMode.BLACKLIST);
				else{
					sender.sendMessage("Unknown spectator mode: "+args[1]);
					return true;
				}
				displayCurrentMode(player);
			}
			else if(args[0].equals("whitelist") || args[0].equals("wl")){
				@SuppressWarnings("deprecation")
				OfflinePlayer spectator = pl.getServer().getOfflinePlayer(args[1]);
				if(spectator == null || spectator.getUniqueId().equals(player.getUniqueId())){
					sender.sendMessage(ChatColor.RED+"Could not find player \""+args[1]+"\"");
				}
				else{
					if(player.addScoreboardTag("spectator_whitelist_"+spectator.getUniqueId())){
						sender.sendMessage(ChatColor.AQUA+spectator.getName()+ChatColor.GRAY+" has been added to your whitelist");
						player.removeScoreboardTag("spectator_blacklist_"+spectator.getUniqueId());
						if(spectator.isOnline()) spectator.getPlayer().sendMessage(
								ChatColor.GRAY+player.getName()+ChatColor.AQUA+" added your to their whitelist");
					}
					else{
						player.removeScoreboardTag("spectator_whitelist_"+spectator.getUniqueId());
						sender.sendMessage(ChatColor.AQUA+spectator.getName()+ChatColor.GRAY+" has been removed from your whitelist");
					}
				}
			}
			else if(args[0].equals("blacklist") || args[0].equals("bl") || args[0].contains("block")){
				@SuppressWarnings("deprecation")
				OfflinePlayer spectator = pl.getServer().getOfflinePlayer(args[1]);
				if(spectator == null || spectator.getUniqueId().equals(player.getUniqueId())){
					sender.sendMessage(ChatColor.RED+"Could not find player \""+args[1]+"\"");
				}
				else{
					if(player.addScoreboardTag("spectator_blacklist_"+spectator.getUniqueId())){
						sender.sendMessage(ChatColor.RED+spectator.getName()
							+ChatColor.GRAY+" has been added to your blacklist");
						player.removeScoreboardTag("spectator_whitelist_"+spectator.getUniqueId());
					}
					else{
						player.removeScoreboardTag("spectator_blacklist_"+spectator.getUniqueId());
						sender.sendMessage(ChatColor.RED+spectator.getName()
							+ChatColor.GRAY+" has been removed from your blacklist");
					}
				}
			}
		}
		return true;
	}
}