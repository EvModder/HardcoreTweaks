package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.HCTweaks;
import Hardcore.SpectatorManager;
import Hardcore.TeleportManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.MethodMocker.CustomPerms;

public class CommandAbout extends EvCommand{
	HCTweaks pl;
	public CommandAbout(HCTweaks pl){super(pl); this.pl = pl;}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		if(args.length > 0){
			args[0] = args[0].toLowerCase();
			ArrayList<String> tabCompletes = new ArrayList<String>();
			for(Player p : pl.getServer().getOnlinePlayers())
				if(p.getName().toLowerCase().startsWith(args[0])) tabCompletes.add(p.getName());
			return tabCompletes;
		}
		return null;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(args.length == 0){
			sender.sendMessage(ChatColor.RED+"Too few arguments");
			return false;
		}
		String name = args[0].toLowerCase();
		final String lastDeath;
		final int numDeaths;
		final int augEvtParticip;
		
		@SuppressWarnings("deprecation")
		Player onlineTarget = pl.getServer().getPlayer(name);
		if(onlineTarget == null) for(Player p : pl.getServer().getOnlinePlayers())
			if(p.getName().toLowerCase().startsWith(name)) onlineTarget = p;
		if(onlineTarget == null){
			@SuppressWarnings("deprecation")
			OfflinePlayer offlineTarget = pl.getServer().getOfflinePlayer(name);
			if(offlineTarget == null){
				pl.getLogger().info("Unknown player: '"+args[0]+"'");
				sender.sendMessage(ChatColor.RED+"Could not find player by name: "+args[0]);
			}
			pl.getLogger().info("Target player: "+offlineTarget.getName());
			lastDeath = HCTweaks.getLastDeath(offlineTarget.getName());
			numDeaths = HCTweaks.getNumDeaths(offlineTarget.getName());
			augEvtParticip = HCTweaks.augEventParicipant(offlineTarget.getUniqueId());
		}
		else{
			pl.getLogger().info("Target player: "+onlineTarget.getName());
			lastDeath = HCTweaks.getLastDeath(onlineTarget.getName());
			numDeaths = HCTweaks.getNumDeaths(onlineTarget.getName());
			augEvtParticip = HCTweaks.augEventParicipant(onlineTarget.getUniqueId());
		}
		// Call essentials:seen
		pl.getServer().getPluginCommand("essentials:seen").execute(
				new CustomPerms(sender, Arrays.asList("essentials.seen"), Arrays.asList()).getProxy(),
				"essentials:seen", args);

		sender.sendMessage(ChatColor.GOLD+" - Last Death: "+ChatColor.RED+lastDeath);
		sender.sendMessage(ChatColor.GOLD+" - Past Lives: "+ChatColor.RED+numDeaths);
		if(augEvtParticip > 0) sender.sendMessage(ChatColor.GOLD+" - Aug'19 Event"+
				(augEvtParticip == 1 ? " participant" : ": "+ChatColor.RED+"champion"));
		if(onlineTarget != null){
			List<String> tps = TeleportManager.get_tp_tags(onlineTarget);
			if(tps.isEmpty()) sender.sendMessage(ChatColor.GOLD+" - No teleport history");
			else{
				Iterator<String> it = tps.iterator();
				StringBuilder builder = new StringBuilder("").append(ChatColor.GOLD)
					.append(" - Unable to teleport: ").append(ChatColor.RED)
					.append(TeleportManager.name_from_tp_tag(it.next()));
				while(it.hasNext()) builder.append(ChatColor.GOLD)
						.append(", ").append(ChatColor.RED)
						.append(TeleportManager.name_from_tp_tag(it.next()));
				sender.sendMessage(builder.toString());
			}
			if(SpectatorManager.isSpectatorFavorYes(onlineTarget)){
				long SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
				long sinceDeath = onlineTarget.getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
				long secondsLeft = SECONDS_UNTIL_RESPAWN - sinceDeath;
				sender.sendMessage(ChatColor.GOLD+" - "+
						SpectatorManager.formatTimeUntilRespawn(
						secondsLeft, ChatColor.RED, ChatColor.GOLD));
			}
			sender.sendMessage(ChatColor.GOLD+" - Spectators: "+ChatColor.WHITE
					+SpectatorManager.getSpectateMode(onlineTarget));
		}
		return true;
	}
}