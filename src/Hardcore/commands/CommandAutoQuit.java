package Hardcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import Hardcore.HCTweaks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.ReflectionUtils;
import net.evmodder.EvLib.extras.ReflectionUtils.RefClass;
import net.evmodder.EvLib.extras.ReflectionUtils.RefField;
import net.evmodder.EvLib.extras.ReflectionUtils.RefMethod;

public class CommandAutoQuit extends EvCommand implements Listener{
	public CommandAutoQuit(HCTweaks pl){
		super(pl);
		//pl.getServer().getPluginManager().registerEvents(this, pl);//TODO
	}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		if(args.length == 1){
			return Arrays.asList("health", "ping");
		}
		else if(args.length == 2){
			if(args[0].equals("health"))
					return Arrays.asList(
							"01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
							"11", "12", "13", "14", "15", "16", "17", "18", "19", "20");
			if(args[0].equals("ping"))
					return Arrays.asList("500", "1000", "2000", "3000", "5000", "9999");
		}
		return null;
	}

	static final RefClass classCraftPlayer = ReflectionUtils.getRefClass("{cb}.entity.CraftPlayer");
	static final RefClass classEntityPlayer = ReflectionUtils.getRefClass("{nms}.EntityPlayer");
	static final RefMethod methodGetHandle = classCraftPlayer.getMethod("getHandle");
	static final RefField fieldPing = classEntityPlayer.getField("ping");
	static int getPing(Player p){
		//java.lang.LinkageError
		return (int) fieldPing.of(methodGetHandle.of(p).call()).get();
	}

	static boolean clear_autoquit_tags(Player player){
		ArrayList<String> removeTags = new ArrayList<String>();
		for(String tag : player.getScoreboardTags()) if(tag.startsWith("autoquit_")) removeTags.add(tag); 
		if(removeTags.isEmpty()) return false;
		for(String tag : removeTags) player.removeScoreboardTag(tag);
		return true;
	}
	static void add_autoquit_health_tag(Player player, int health){
		for(int i=1; i<21; ++i) player.removeScoreboardTag("autoquit_health_"+i);
		player.addScoreboardTag("autoquit_health_"+health);
	}
	static void add_autoquit_ping_tag(Player player, int ping){
		String oldPingTag = null;
		for(String tag : player.getScoreboardTags()) if(tag.startsWith("autoquit_ping_")) oldPingTag = tag;
		if(oldPingTag != null) player.removeScoreboardTag(oldPingTag);
		player.addScoreboardTag("autoquit_ping_"+ping);
	}
	static boolean check_autoquit_tags(Player player, double health){
		for(int i=20; i>health; --i) if(player.removeScoreboardTag("autoquit_health_"+i)) return true;
		String pingTag = null;
		for(String tag : player.getScoreboardTags()) if(tag.startsWith("autoquit_ping_")) pingTag = tag;
		if(pingTag != null && getPing(player) >= Integer.parseInt(pingTag.substring(14))){
			player.removeScoreboardTag(pingTag);
			return true;
		}
		return false;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}
		if(args.length == 0){
			if(clear_autoquit_tags((Player)sender)){
				sender.sendMessage(ChatColor.AQUA+"Cleared AutoQuit triggers");
				return true;
			}
			else args = new String[]{"health", "20"};
		}
		args[0] = args[0].toLowerCase();
		if(args.length == 1){
			if(args[0].equals("ping")) args = new String[]{"ping", "9999"}; 
			else args = new String[]{"health", args[0]};
		}

		if(args[0].equals("health")){
			try{
				int health = args[1].toLowerCase().trim().equals("any") ? 20 : Integer.parseInt(args[1]);
				if(health > 20 || health < 1){
					sender.sendMessage(ChatColor.RED+"Invalid health amount");
				}
				else{
					sender.sendMessage(ChatColor.AQUA
							+"You will now be automatically disconnected if your health drops below "
							+ChatColor.WHITE+health);
					add_autoquit_health_tag((Player)sender, health);
				}
			}
			catch(NumberFormatException ex){
				sender.sendMessage(ChatColor.RED+"Invalid health amount");
			}
		}
		else if(args[0].equals("ping")){
			try{
				int ping = args[1].toLowerCase().trim().equals("any") ? 20 : Integer.parseInt(args[1]);
				if(ping < 500){
					sender.sendMessage(ChatColor.RED+"Invalid ping amount (minimum is 500");
				}
				else{
					sender.sendMessage(ChatColor.AQUA+"You will now be automatically disconnected "
							+ "if you take damage when your ping is below "
							+ChatColor.WHITE+ping);
					add_autoquit_ping_tag((Player)sender, ping);
				}
			}
			catch(NumberFormatException ex){
				sender.sendMessage(ChatColor.RED+"Invalid ping number");
			}
		}
		else sender.sendMessage(ChatColor.RED+"Unknown argument");
		return true;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDamageTake(EntityDamageEvent evt){
		if(evt.getEntity() instanceof Player){
			Player player = (Player)evt.getEntity();
			double resultingHealth = player.getHealth() - evt.getFinalDamage();
			if(check_autoquit_tags(player, resultingHealth)){
				final UUID uuid = player.getUniqueId();
				new BukkitRunnable(){@Override public void run(){
					Player p = HCTweaks.getPlugin().getServer().getPlayer(uuid);
					if(p != null && check_autoquit_tags(p, resultingHealth))
						p.kickPlayer(ChatColor.RED+"Triggered AutoQuit due to low health["
								+ChatColor.GRAY+resultingHealth+ChatColor.RED+"] or ping["
								+ChatColor.GRAY+getPing(player)+"]");
				}}.runTaskLater(HCTweaks.getPlugin(), 2);
			}
		}
	}
}