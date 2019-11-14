package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.HCTweaks;
import Hardcore.SpectatorManager;
import Hardcore.TeleportManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.MethodMocker;

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

	String getNameFromTpTag(String tag){
		try{
			OfflinePlayer player = pl.getServer().getOfflinePlayer(UUID.fromString(tag.substring(3)));
			return player.getName();
		}
		catch(NullPointerException | IllegalArgumentException ex){return null;}
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
		if(sender instanceof Player) pl.getServer().getPluginCommand("essentials:seen").execute(
				(Player)MethodMocker.getProxy(
						(Player)sender, new Class<?>[]{Player.class},
						Collections.singletonMap("hasPermission",
								x -> x.toString().equals("essentials.seen") || sender.hasPermission(x.toString())),
						false
				)
		/*new CommandSender(){
			@Override public Set<PermissionAttachmentInfo> getEffectivePermissions(){return sender.getEffectivePermissions();}
			@Override public boolean isPermissionSet(String perm){return sender.isPermissionSet(perm);}
			@Override public boolean isPermissionSet(Permission perm){return sender.isPermissionSet(perm);}
			@Override public void recalculatePermissions(){sender.recalculatePermissions();}
			@Override public void removeAttachment(PermissionAttachment pa){sender.removeAttachment(pa);}
			@Override public boolean isOp(){return sender.isOp();}
			@Override public void setOp(boolean op){sender.setOp(op);}
			@Override public String getName(){return sender.getName();}
			@Override public Server getServer(){return sender.getServer();}
			@Override public void sendMessage(String msg){sender.sendMessage(msg);}
			@Override public void sendMessage(String[] msgs){sender.sendMessage(msgs);}
			@Override public PermissionAttachment addAttachment(Plugin pl){return sender.addAttachment(pl);}
			@Override public PermissionAttachment addAttachment(Plugin pl, int i){return sender.addAttachment(pl, i);}
			@Override public PermissionAttachment addAttachment(Plugin pl, String s, boolean b){
				return sender.addAttachment(pl, s, b);}
			@Override public PermissionAttachment addAttachment(Plugin pl, String s, boolean b, int i){
				return sender.addAttachment(pl, s, b, i);}

			@Override public boolean hasPermission(String perm){
				return perm.equals("essentials.seen") || sender.hasPermission(perm);}
			@Override public boolean hasPermission(Permission perm){
				return perm.toString().equals("essentials.seen") || sender.hasPermission(perm);}
		}*/
				, "essentials:seen", args);
		else pl.getServer().getPluginCommand("essentials:seen").execute(sender, "essentials:seen", args);

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
					.append(getNameFromTpTag(it.next()));
				while(it.hasNext()) builder.append(ChatColor.GOLD)
						.append(", ").append(ChatColor.RED)
						.append(getNameFromTpTag(it.next()));
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