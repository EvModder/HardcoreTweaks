package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.Extras;
import Hardcore.HCTweaks;
import Hardcore.SpectatorManager;
import Hardcore.TeleportManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
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
			if(sender instanceof Player == false){
				sender.sendMessage(ChatColor.RED+"Too few arguments");
				return false;
			}
			args = new String[]{sender.getName()};
		}
		args[0] = args[0].toLowerCase();
		final String name;;
		final UUID uuid;
		Player onlineTarget = pl.getServer().getPlayer(args[0]);
		if(onlineTarget == null) for(Player p : pl.getServer().getOnlinePlayers())
			if(p.getName().toLowerCase().startsWith(args[0])) onlineTarget = p;
		if(onlineTarget == null){
			@SuppressWarnings("deprecation")
			OfflinePlayer offlineTarget = pl.getServer().getOfflinePlayer(args[0]);
			if(offlineTarget == null){
				sender.sendMessage(ChatColor.RED+"Could not find player by name: "+args[0]);
				return true;
			}
			name = offlineTarget.getName();
			uuid = offlineTarget.getUniqueId();
		}
		else{
			name = onlineTarget.getName();
			uuid = onlineTarget.getUniqueId();
		}

		// Call /essentials:seen using CustomPerms wrapper
		pl.getServer().getPluginCommand("essentials:seen").execute(
				new CustomPerms(sender, Arrays.asList("essentials.seen"), Arrays.asList()).getProxy(),
				"essentials:seen", args);


		// Send data sourced from server files
		sender.sendMessage(ChatColor.GOLD+" - Last Death: "+ChatColor.RED+HCTweaks.getLastDeath(name));
		sender.sendMessage(ChatColor.GOLD+" - Past Lives: "+ChatColor.RED+HCTweaks.getNumDeaths(name));
		final String aug19Evt = Extras.eventStatusAug19Build(uuid); if(aug19Evt != null) sender.sendMessage(aug19Evt);
		final String feb20Evt = Extras.eventStatusFeb20Equine(uuid); if(feb20Evt != null) sender.sendMessage(feb20Evt);

		// Send online-player-only data (tag checking)
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
			if(SpectatorManager.isSpectator(onlineTarget)){
				long SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
				long frequentDeathPenalty = SpectatorManager.getFrequentDeathRespawnPenalty(onlineTarget);
				long sinceDeath = onlineTarget.getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
				long secondsLeft = SECONDS_UNTIL_RESPAWN + frequentDeathPenalty - sinceDeath;
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