package Hardcore;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.EvLib.extras.ActionBarUtils;

public class SpectatorManager implements Listener{
	static HCTweaks pl;
	static HashSet<UUID> spectators;
	final int MAX_DIST_SQ = 32*32;
	final long SECONDS_UNTIL_RESPAWN;
	final float FLY_SPEED = 0.1f;

	public SpectatorManager(HCTweaks plugin){
		pl = plugin;
		SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
		if(pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Spectators") == null){
			pl.getServer().getScoreboardManager().getMainScoreboard().registerNewTeam("Spectators");
		}
		spectators = new HashSet<UUID>();
		loopActive = false;
		runSpecatorLoop();
	}

	static boolean isSpectator(Player player){
		return !player.isOp() && (
				(player.getGameMode() == GameMode.SPECTATOR || player.isDead()) &&
				player.getScoreboardTags().contains("dead")
		);
	}

	public static boolean isSpectatorFavorYes(Player player){
		return player.getGameMode() != GameMode.SURVIVAL ||
				player.isDead() || player.getScoreboardTags().contains("dead") ||
				(spectators != null && spectators.contains(player.getUniqueId()));
	}

	static Location getClosest(Location loc, HashSet<Location> points){
		Location closest = null;
		double cDist = Double.MAX_VALUE;
		for(Location point : points){
			if(!point.getWorld().getUID().equals(loc.getWorld().getUID())) continue;
			double pDist = loc.distanceSquared(point);
			if(pDist < cDist){
				closest = point;
				cDist = pDist;
			}
		}
		return closest != null ? closest : (points.isEmpty() ? null : points.iterator().next());
	}

	static Player getClosestGm0(Location loc){
		double closestDistGm0 = Double.MAX_VALUE;
		Player closestPlayer = null;
		for(Player p : loc.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL &&
					(dist=p.getLocation().distanceSquared(loc)) < closestDistGm0){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		if(closestPlayer == null){
			for(Player p : org.bukkit.Bukkit.getOnlinePlayers()){
				if(p.getGameMode() == GameMode.SURVIVAL) return p;
			}
		}
		return closestPlayer;
	}
	static boolean canSpectator(Player spectator, Player target){
		String bl_tag = "spectator_blacklist_"+spectator.getUniqueId();
		String wl_tag = "spectator_whitelist_"+spectator.getUniqueId();
		return target.getScoreboardTags().contains("blacklist_mode") ?
				!target.getScoreboardTags().contains(bl_tag) :
				target.getScoreboardTags().contains(wl_tag);
	}
	static Player getClosestGm0WithPerms(Player toPlayer){
		double closestDistGm0 = Double.MAX_VALUE;
		Player closestPlayer = null;
		for(Player p : toPlayer.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL && canSpectator(toPlayer, p) &&
					(dist=p.getLocation().distanceSquared(toPlayer.getLocation())) < closestDistGm0){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		if(closestPlayer == null){
			for(Player p : toPlayer.getServer().getOnlinePlayers()){
				if(p.getGameMode() == GameMode.SURVIVAL) return p;
			}
		}
		return closestPlayer;
	}
	static Player getNearbyGm0(Location loc){
		double closestDistGm0 = 10000D;
		Player closestPlayer = null;
		for(Player p : loc.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL &&
					(dist=p.getLocation().distanceSquared(loc)) < closestDistGm0){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		return closestPlayer;
	}

	public static String formatTimeUntilRespawn(long secondsLeft, ChatColor numC, ChatColor textC){
		long minutesLeft = secondsLeft / 60, hoursLeft = minutesLeft / 60, daysLeft = hoursLeft / 24;
		secondsLeft %= 60; minutesLeft %= 60; hoursLeft %= 24;
		//Too spammy
		/*String respawnDisplayCmd
				= "title "+specP.getName()+" actionbar [\"\","
				+ "{\"text\":\"Respawn in: \",\"color\":\"gray\"},"
				+ "{\"text\":\""+hoursLeft+"\",\"color\":\"yellow\"},"
				+ "{\"text\":\":\",\"color\":\"gray\"},"
				+ "{\"text\":\""+minutesLeft+"\",\"color\":\"yellow\"},"
				+ "{\"text\":\":\",\"color\":\"gray\"},"
				+ "{\"text\":\""+secondsLeft+"\",\"color\":\"yellow\"}]";
		pl.runCommand(respawnDisplayCmd);*/
		StringBuilder builder = new StringBuilder("")
				.append(textC).append("Respawn in: ").append(numC);
		if(daysLeft > 0) builder.append(daysLeft)
				.append(textC).append('d').append(numC);
		if(hoursLeft > 0) builder.append(hoursLeft < 10 ? "0"+hoursLeft : hoursLeft)
				.append(textC).append('h').append(numC);
		if(minutesLeft > 0) builder.append(minutesLeft < 10 ? "0"+minutesLeft : minutesLeft)
				.append(textC).append('m').append(numC);
		if(secondsLeft > 0) builder.append(secondsLeft < 10 ? "0"+secondsLeft : secondsLeft)
				.append(textC).append('s');
		else builder.append("now");
		return builder.toString();
	}

	boolean loopActive = false;
	void runSpecatorLoop(){
		if(loopActive) return;
		loopActive = true;
		new BukkitRunnable(){@SuppressWarnings("unchecked") @Override public void run(){
			//TODO: if(!"enable-spectating") for spectator p.kickPlayer()
			HashSet<Location> nonSpecLocs = new HashSet<Location>();
			for(Player p : pl.getServer().getOnlinePlayers()){
				if(isSpectator(p)) addSpectator(p);
				else if(p.getGameMode() == GameMode.SURVIVAL) nonSpecLocs.add(p.getLocation());
			}
			if(nonSpecLocs.isEmpty()){
				for(UUID uuid : (Collection<UUID>)spectators.clone()){
					OfflinePlayer p = pl.getServer().getPlayer(uuid);
					if(p != null && p.isOnline()){
						p.getPlayer().kickPlayer(ChatColor.RED+"There is nobody online to spectate right now");
						p.getPlayer().setFlySpeed(0.2f);
						p.getPlayer().getScoreboard().getTeam("Spectators").removeEntry(p.getName());
					}
				}
				spectators.clear();
			}
			else{
				Iterator<UUID> it = spectators.iterator();
				while(it.hasNext()){
					UUID uuid = it.next();
					OfflinePlayer p = pl.getServer().getPlayer(uuid);
					if(p == null || !p.isOnline() || !isSpectator(p.getPlayer())) it.remove();
				}
			}
			if(spectators.isEmpty()){
				pl.getLogger().info("No spectators remaining, setting loopAtive=false");
				//HandlerList.unregisterAll(SpectatorListener.this);
				cancel();
				loopActive = false;
			}
			else{
				for(UUID uuid : spectators){
					Player specP = pl.getServer().getPlayer(uuid).getPlayer();
					Location aliveP = getClosest(specP.getLocation(), nonSpecLocs);
					if(!specP.getWorld().getUID().equals(aliveP.getWorld().getUID())
							|| specP.getLocation().distanceSquared(aliveP) > MAX_DIST_SQ){
						specP.teleport(aliveP);
					}
					int secondsSinceDeath = specP.getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
					long secondsLeft = SECONDS_UNTIL_RESPAWN - secondsSinceDeath;
					if(secondsLeft <= 0){
						new BukkitRunnable(){@Override public void run(){
							
							specP.kickPlayer(ChatColor.GREEN+"You may now respawn!");
						}}.runTaskLater(pl, 1);
						secondsLeft = 0;
						new BukkitRunnable(){
							int attempts = 0;
							@Override public void run(){
								//Make sure they're offline
								Player p = pl.getServer().getPlayer(uuid);
								if((p == null && pl.deletePlayerdata(uuid)) || ++attempts == 10) cancel();
							}
						}.runTaskTimer(pl, 5, 20);
						continue;
					}
					ActionBarUtils.sendToPlayer(
							formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY), specP);
				}
			}
		}}.runTaskTimer(pl, 20, 20);
	}

	public void addSpectator(Player player){
		if(spectators.add(player.getUniqueId())){
			pl.getLogger().info("Added spectator: "+player.getName());
			player.setFlySpeed(FLY_SPEED);
//			pl.setPermission(player, "essentials.tpa", false);
//			pl.setPermission(player, "essentials.tpahere", false);
//			pl.setPermission(player, "essentials.tpaccept", false);
			player.getScoreboard().resetScores(player.getName());
			runSpecatorLoop();
		}
	}
	public boolean removeSpectator(Player player){
		pl.getServer().getScoreboardManager().getMainScoreboard()
					.getTeam("Spectators").removeEntry(player.getName());
		//player.getPlayer().setFlySpeed(0.2f);
		if(spectators.remove(player.getUniqueId())){
			pl.getLogger().info("Removed spectator: "+player.getName());
			return true;
		}
		return false;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent evt){
		if(removeSpectator(evt.getPlayer())
				&& evt.getPlayer().getScoreboardTags().contains("dead")){
			evt.getPlayer().getScoreboard().resetScores(evt.getPlayer().getName());
			int ticksSinceDeath = evt.getPlayer().getStatistic(Statistic.TIME_SINCE_DEATH);
			int hrsSinceDeath = ticksSinceDeath/(20*60*60);
			pl.getLogger().info("Ticks since death: "+ticksSinceDeath);
			pl.getLogger().info("Hours since death: "+(((double)ticksSinceDeath)/(20*60*60)));
			if(hrsSinceDeath >= 24){
				//Reset playerdata & stats so next time they log in they will respawn :)
				final UUID uuid = evt.getPlayer().getUniqueId();
				new BukkitRunnable(){@Override public void run(){
					//Make sure they're offline
					Player p = pl.getServer().getPlayer(uuid);
					if(p == null) pl.deletePlayerdata(uuid);
				}}.runTaskLater(pl, 5);
			}
		}
	}

	@EventHandler
	public void onLogin(PlayerLoginEvent evt){
		final UUID uuid = evt.getPlayer().getUniqueId();
		OfflinePlayer offP = pl.getServer().getOfflinePlayer(uuid);
		final long millisSinceLastLogin = System.currentTimeMillis() - offP.getLastPlayed();
		final int ticksSinceLastLogin = (int)(millisSinceLastLogin/50);
		if(ticksSinceLastLogin > 0){
			new BukkitRunnable(){@Override public void run(){
				Player p = pl.getServer().getPlayer(uuid);
				if(p != null && isSpectator(p)){
					addSpectator(evt.getPlayer());
					final double inHrs = ((double)ticksSinceLastLogin)/(20*60*60);
					pl.getLogger().info("Adding: "+inHrs+"h to SinceLastDeath (ticks="+ticksSinceLastLogin+")");
					p.incrementStatistic(Statistic.TIME_SINCE_DEATH, ticksSinceLastLogin);
				}
			}}.runTaskLater(pl, 20);
		}
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onJoin(PlayerJoinEvent evt){
		if(isSpectatorFavorYes(evt.getPlayer())) pl.getServer().getScoreboardManager().getMainScoreboard()
			.getTeam("Spectators").addEntry(evt.getPlayer().getName());
		if(isSpectator(evt.getPlayer())) addSpectator(evt.getPlayer());
	}

	@EventHandler
	public void onGameModeChange(PlayerGameModeChangeEvent evt){
		if(evt.getNewGameMode() != GameMode.SURVIVAL){
			if(isSpectator(evt.getPlayer())) addSpectator(evt.getPlayer());
			pl.getServer().getScoreboardManager().getMainScoreboard()
				.getTeam("Spectators").addEntry(evt.getPlayer().getName());
		}
		else removeSpectator(evt.getPlayer());
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent evt){
		final UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){
			Player p = pl.getServer().getPlayer(uuid);
			if(p != null) addSpectator(p);
		}}.runTaskLater(pl, 20*5);
	}
}