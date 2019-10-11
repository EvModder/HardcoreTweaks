package Hardcore.commands;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;
import Hardcore.HCTweaks;
import Hardcore.SpectatorManager;
import Hardcore.TeleportManager;

public class TEMP_CmdSeen_CmdPing implements Listener {
	final HCTweaks pl;

	public TEMP_CmdSeen_CmdPing(HCTweaks plugin){
		pl = plugin;
	}

	String getNameFromTpTag(String tag){
		try{
			OfflinePlayer player = pl.getServer().getOfflinePlayer(UUID.fromString(tag.substring(3)));
			return player.getName();
		}
		catch(NullPointerException | IllegalArgumentException ex){return null;}
	}

	@EventHandler @SuppressWarnings("deprecation")
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().charAt(0) != '/' || evt.isCancelled()) return;
		String message = evt.getMessage().trim();
		String command = message.toLowerCase().trim();
		int space = command.indexOf(' ');
		command = (space > 0 ? command.substring(1, space) : command.substring(1));
		Player player = evt.getPlayer();

		if(command.equals("seen")){
			if(player.hasPermission("hardcore.seen") && space > 0){
				String name = message.substring(space + 1);
				OfflinePlayer target = pl.getServer().getOfflinePlayer(name);
				if(target != null){
					pl.getLogger().info("Target player: "+target.getName());
					final String lastDeath = HCTweaks.getLastDeath(target.getName());
					final int numDeaths = HCTweaks.getNumDeaths(target.getName());
					final int augEvtParticip = HCTweaks.augEventParicipant(target.getUniqueId());
					final UUID uuid = player.getUniqueId();
					new BukkitRunnable(){@Override public void run(){
						Player player = pl.getServer().getPlayer(uuid);
						if(player != null){
							player.sendMessage(ChatColor.GOLD+" - Last Death: "+ChatColor.RED+lastDeath);
							player.sendMessage(ChatColor.GOLD+" - Past Lives: "+ChatColor.RED+numDeaths);
							if(augEvtParticip > 0){
								player.sendMessage(ChatColor.GOLD+" - Aug'19 Event"+
							(augEvtParticip == 1 ? " participant" : ": "+ChatColor.RED+"champion"));
							}
							//TODO: join date(?), used TPs
							if(target.isOnline()){
								List<String> tps = TeleportManager.get_tp_tags(target.getPlayer());
								if(tps.isEmpty()){
									player.sendMessage(ChatColor.GOLD+" - No teleport history");
								}
								else{
									Iterator<String> it = tps.iterator();
									StringBuilder builder = new StringBuilder("").append(ChatColor.GOLD)
										.append(" - Unable to teleport: ").append(ChatColor.RED)
										.append(getNameFromTpTag(it.next()));
									while(it.hasNext()) builder.append(ChatColor.GOLD)
											.append(", ").append(ChatColor.RED)
											.append(getNameFromTpTag(it.next()));
									player.sendMessage(builder.toString());
								}
								if(SpectatorManager.isSpectatorFavorYes(target.getPlayer())){
									long SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
									long sinceDeath = target.getPlayer().getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
									long secondsLeft = SECONDS_UNTIL_RESPAWN - sinceDeath;
									player.sendMessage(ChatColor.GOLD+" - "+
											SpectatorManager.formatTimeUntilRespawn(
											secondsLeft, ChatColor.RED, ChatColor.GOLD));
								}
								player.sendMessage(ChatColor.GOLD+" - Spectators: "+ChatColor.WHITE
										+SpectatorManager.getSpectateMode(target.getPlayer()));
							}
						}
					}}.runTaskLater(pl, 2);
				}
				else pl.getLogger().info("Unknown player: '"+name+"'");
				if(!player.hasPermission("essentials.seen")) evt.setCancelled(true);
			}
		}
		else if(command.equals("ping")){
			Player target = space == -1 ? player : pl.getServer().getPlayer(message.substring(space + 1));
			if(target != null){
				player.sendMessage(ChatColor.GOLD+"Ping: "+ChatColor.GREEN+CommandAutoQuit.getPing(target));
			}
		}
	}
}