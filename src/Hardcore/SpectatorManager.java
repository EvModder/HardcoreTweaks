package Hardcore;

import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.EvLib.extras.ActionBarUtils;

public class SpectatorManager implements Listener{
	final HCTweaks pl;
	public enum WatchMode {BLACKLIST, WHITELIST};
	static WatchMode DEFAULT_MODE;
	final int MAX_DIST_SQ = 32*32;
	final long SECONDS_UNTIL_RESPAWN;
	final float FLY_SPEED = 0.01f;
	static HashSet<UUID> spectators;

	public SpectatorManager(HCTweaks plugin){
		pl = plugin;
		SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
		if(pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Spectators") == null){
			pl.getServer().getScoreboardManager().getMainScoreboard().registerNewTeam("Spectators");
		}
		DEFAULT_MODE = pl.getConfig().getString("spectator-mode", "blacklist").equals("blacklist") ?
				WatchMode.BLACKLIST : WatchMode.WHITELIST;
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

	public static boolean canSpectate(UUID spectator, Player target){
		String bl_tag = "spectator_blacklist_"+spectator;
		String wl_tag = "spectator_whitelist_"+spectator;
		boolean ans= !target.getScoreboardTags().contains(bl_tag) &&
				(getSpectateMode(target) != WatchMode.WHITELIST
				|| target.getScoreboardTags().contains(wl_tag));
		org.bukkit.Bukkit.getLogger().info(org.bukkit.Bukkit.getOfflinePlayer(spectator).getName()
				+ " able to spectate "+target.getName()+": "+ans);
		return ans;
	}
	public static WatchMode getSpectateMode(Player player){
		return player.getScoreboardTags().contains("whitelist_mode") ? WatchMode.WHITELIST
				: player.getScoreboardTags().contains("blacklist_mode") ? WatchMode.BLACKLIST
				: DEFAULT_MODE;
	}
	public static void setSpectateMode(Player player, WatchMode mode){
		if(mode == WatchMode.WHITELIST){
			player.addScoreboardTag("whitelist_mode");
			player.removeScoreboardTag("blacklist_mode");
		}
		else if(mode == WatchMode.BLACKLIST){
			player.addScoreboardTag("blacklist_mode");
			player.removeScoreboardTag("whitelist_mode");
		}
		else org.bukkit.Bukkit.getLogger().severe("Unknown spectate mode: "+mode);
	}
	public static boolean isDefaultSpectateMode(Player player){
		return !player.getScoreboardTags().contains("whitelist_mode") && 
				!player.getScoreboardTags().contains("blacklist_mode");
	}

	static Player getClosestGm0WithPerms(Location loc, Player spec){
		double closestDistGm0 = Double.MAX_VALUE;
		Player closestPlayer = null;
		for(Player p : loc.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL && (spec == null || canSpectate(spec.getUniqueId(), p)) &&
					(dist=p.getLocation().distanceSquared(loc)) < closestDistGm0){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		if(closestPlayer == null){
			for(Player p : org.bukkit.Bukkit.getServer().getOnlinePlayers()){
				if(p.getGameMode() == GameMode.SURVIVAL) return p;
			}
		}
		return closestPlayer;
	}
	static Player getNearbyGm0WithPerms(Location loc, Player spec){
		double closestDistGm0 = 10000D;
		Player closestPlayer = null;
		for(Player p : loc.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL && (spec == null || canSpectate(spec.getUniqueId(), p)) &&
					(dist=p.getLocation().distanceSquared(loc)) < closestDistGm0){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		return closestPlayer;
	}

	public static String formatTimeUntilRespawn(long SECONDS_LEFT, ChatColor numC, ChatColor textC){
		long secondsLeft = SECONDS_LEFT;
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
		if(SECONDS_LEFT > 0) builder.append(secondsLeft < 10 ? "0"+secondsLeft : secondsLeft)
				.append(textC).append('s');
		else builder.append("now");
		return builder.toString();
	}

	boolean loopActive = false;
	void runSpecatorLoop(){
		if(loopActive) return;
		loopActive = true;
		new BukkitRunnable(){@Override public void run(){
			//TODO: if(!"enable-spectating") for spectator p.kickPlayer()
			for(Player p : pl.getServer().getOnlinePlayers()) if(isSpectator(p)) addSpectator(p);
			//p.kickPlayer(ChatColor.RED+"There is nobody online who you can spectate right now");
			Iterator<UUID> it = spectators.iterator();
			while(it.hasNext()){
				UUID uuid = it.next();
				OfflinePlayer p = pl.getServer().getPlayer(uuid);
				if(p == null || !p.isOnline() || !isSpectator(p.getPlayer())) it.remove();
			}
			if(spectators.isEmpty()){
				pl.getLogger().info("No spectators remaining, setting loopAtive=false");
				//HandlerList.unregisterAll(SpectatorListener.this);
				cancel();
				loopActive = false;
			}
			for(UUID uuid : spectators){
				Player specP = pl.getServer().getPlayer(uuid).getPlayer();
				specP.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1000000, 0, true));

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
				}
				else ActionBarUtils.sendToPlayer(
						formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY), specP);

				if(specP.isDead()) continue;
				Entity target = specP.getSpectatorTarget();
				if(target == null || !(target instanceof Player)){
					Player newTarget = getClosestGm0WithPerms(specP.getLocation(), specP);
					if(newTarget == null){
						specP.kickPlayer(ChatColor.RED+"There is nobody online who you can spectate right now");
					}
					else{
						UUID targetUUID = newTarget.getUniqueId();
						specP.setSpectatorTarget(newTarget);
						new BukkitRunnable(){@Override public void run(){
							specP.setSpectatorTarget(null);
							Player target = pl.getServer().getPlayer(targetUUID);
							if(target != null) specP.setSpectatorTarget(newTarget);
						}}.runTaskLater(pl, 20);
						new BukkitRunnable(){@Override public void run(){
							specP.setSpectatorTarget(null);
							Player target = pl.getServer().getPlayer(targetUUID);
							if(target != null) specP.setSpectatorTarget(newTarget);
						}}.runTaskLater(pl, 40);
						new BukkitRunnable(){@Override public void run(){
							specP.setSpectatorTarget(null);
							Player target = pl.getServer().getPlayer(targetUUID);
							if(target != null) specP.setSpectatorTarget(newTarget);
						}}.runTaskLater(pl, 80);
					}
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
			long secondsSinceDeath = ticksSinceDeath/20;
			pl.getLogger().info("Ticks since death: "+ticksSinceDeath);
			pl.getLogger().info("Hours since death: "+(secondsSinceDeath/(60*60)));
			if(secondsSinceDeath >= SECONDS_UNTIL_RESPAWN){
				//Reset playerdata & stats so next time they log in they will respawn :)
				final UUID uuid = evt.getPlayer().getUniqueId();
				new BukkitRunnable(){@Override public void run(){
					//Make sure they're offline
					Player p = pl.getServer().getPlayer(uuid);
					if(p == null) pl.deletePlayerdata(uuid);
				}}.runTaskLater(pl, 5);
				new BukkitRunnable(){@Override public void run(){
					//Make sure they're offline
					Player p = pl.getServer().getPlayer(uuid);
					if(p == null) pl.deletePlayerdata(uuid);
				}}.runTaskLater(pl, 20);
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