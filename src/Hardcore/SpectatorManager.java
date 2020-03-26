package Hardcore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.extras.ActionBarUtils;
import net.evmodder.EvLib.util.Pair;

public class SpectatorManager implements Listener{
	final HCTweaks pl;
	public enum WatchMode {BLACKLIST, WHITELIST};
	static WatchMode DEFAULT_MODE;
	final int MAX_DIST_SQ = 20*20;
	final long SECONDS_UNTIL_RESPAWN;
	final float FLY_SPEED = 0.08f;
	static HashSet<UUID> spectators;
	final Location WORLD_SPAWN;
	static final boolean CLOSEST_ACROSS_DIMENSIONS = true;

	public SpectatorManager(HCTweaks plugin){
		pl = plugin;
		SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
		if(pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Spectators") == null){
			pl.getServer().getScoreboardManager().getMainScoreboard().registerNewTeam("Spectators");
		}
		DEFAULT_MODE = pl.getConfig().getString("spectator-mode", "blacklist").equals("blacklist") ?
				WatchMode.BLACKLIST : WatchMode.WHITELIST;
		spectators = new HashSet<UUID>();
		WORLD_SPAWN = new Location(pl.getServer().getWorlds().get(0), 0, 90, 0);
		loopActive = false;
		runSpecatorLoop();
	}

	public static boolean isSpectator(Player player){
		return (player.getGameMode() == GameMode.SPECTATOR || player.isDead())
				&& player.getScoreboardTags().contains("dead");
	}

	static boolean isSpectatorFavorYes(Player player){
		return player.getGameMode() != GameMode.SURVIVAL &&
				(player.isDead() || player.getScoreboardTags().contains("dead") ||
				(spectators != null && spectators.contains(player.getUniqueId())));
	}

	public static boolean canSpectate(UUID spectator, Player target){
		//TODO: TEMP, remove after archery event
		if(target.getLocation().getX() < -29990000 && target.getLocation().getZ() < -29990000) return false;
		if(spectator.equals(target.getUniqueId())) return false;
		String bl_tag = "spectator_blacklist_"+spectator;
		String wl_tag = "spectator_whitelist_"+spectator;
		boolean ans = !target.getScoreboardTags().contains(bl_tag) &&
				(getSpectateMode(target) != WatchMode.WHITELIST
				|| target.getScoreboardTags().contains(wl_tag));
		//org.bukkit.Bukkit.getLogger().info(org.bukkit.Bukkit.getOfflinePlayer(spectator).getName()
		//		+ " able to spectate "+target.getName()+": "+ans);
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

	public static long getFrequentDeathRespawnPenalty(Player spectator){
		for(String tag : spectator.getScoreboardTags()){
			if(tag.startsWith("respawn_penalty=")) return Long.parseLong(tag.substring(16));
		}
		return 0;
	}

	static Player getClosestGm0WithPerms(Location loc, Player spec){
		double closestDistGm0 = Double.MAX_VALUE;
		Player closestPlayer = null;
		if(!CLOSEST_ACROSS_DIMENSIONS) for(Player p : loc.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL && (spec == null || canSpectate(spec.getUniqueId(), p)) &&
					(dist=p.getLocation().distanceSquared(loc)) < closestDistGm0){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		if(closestPlayer == null){
			double dist;
			for(Player p : org.bukkit.Bukkit.getServer().getOnlinePlayers()){
				if(p.getGameMode() == GameMode.SURVIVAL && (spec == null || canSpectate(spec.getUniqueId(), p))){
					if((dist=Extras.crossDimensionalDistanceSquared(loc, p.getLocation())) <= closestDistGm0){
						closestDistGm0 = dist;
						closestPlayer = p;
					}
				}
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

	private void sendSpectateBeginNotice(Player spectator, Player target, Location cameFrom){
		//if(target != null) pl.getLogger().info(spectator.getName()+" is now spectating "+target.getName());
		spectator.sendMessage(ChatColor.GRAY+"You are now spectating "+ChatColor.AQUA+target.getDisplayName());
		if(!spectator.hasPermission("hardcore.spectator.bypass.notify")
				&& target != null
				&& !target.getScoreboardTags().contains("mute_spectate_notify")
				&& (cameFrom == null || !target.getWorld().getUID().equals(cameFrom.getWorld().getUID())
					|| target.getLocation().distanceSquared(cameFrom) > 100*100))
			target.sendMessage("§7§l+ §b"+spectator.getDisplayName()+"§7 is now spectating you");
	}
	private void sendSpectateEndNotice(Player spectator, Player target, Location leftTo){
		//if(target != null) pl.getLogger().info(spectator.getName()+" is no longer spectating "+target.getName());
		//specP.sendMessage(ChatColor.GRAY+"You are no longer spectating "+ChatColor.AQUA+target.getDisplayName());
		if(!spectator.hasPermission("hardcore.spectator.bypass.notify")
				&& target != null
				&& !target.getScoreboardTags().contains("mute_spectate_notify")
				&& (leftTo == null || !target.getWorld().getUID().equals(leftTo.getWorld().getUID())
					|| target.getLocation().distanceSquared(leftTo) > 100*100))
			target.sendMessage("§7§l- §b"+spectator.getDisplayName()+"§7 is no longer spectating you");
	}

	private boolean canSeeThroughBlocks(Location eyeLoc){
		Block b = eyeLoc.getBlock();
		if(!b.getType().isOccluding()) return false;
		final BlockFace[] snoopDirs = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
		int snoopScore = 0;
		for(BlockFace face : snoopDirs) if(b.getRelative(face).getType().isOccluding()) ++snoopScore;
		return snoopScore >= 3;
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

				int secondsSinceDeath = specP.getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
				long frequentDeathPenalty = getFrequentDeathRespawnPenalty(specP);
				long secondsLeft = SECONDS_UNTIL_RESPAWN + frequentDeathPenalty - secondsSinceDeath;
				if(secondsLeft <= 0) ActionBarUtils.sendToPlayer(
						ChatColor.GREEN+"You may now respawn with "+ChatColor.AQUA+"/respawn", specP);
				else ActionBarUtils.sendToPlayer(
						formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY), specP);

				if(specP.isDead() || specP.isOp()) continue;
				Entity target = specP.getSpectatorTarget();
				if(target == null || !(target instanceof Player) || isSpectatorFavorYes((Player)target)){
					Player newTarget = getClosestGm0WithPerms(specP.getLocation(), specP);
					if(newTarget == null){
						if(specP.getScoreboardTags().contains("spectating")){
							specP.removeScoreboardTag("spectating");
							if(!specP.hasPermission("hardcore.spectator.bypass.slowness")) specP.setFlySpeed(FLY_SPEED);
							specP.removePotionEffect(PotionEffectType.BLINDNESS);
							specP.teleport(WORLD_SPAWN, TeleportCause.CHORUS_FRUIT);//CHORUS_FRUIT is a hack to bypass TPmanager
							specP.sendTitle("", "There is nobody who you can spectate", 10, 20*60, 20);
							new BukkitRunnable(){@Override public void run(){
								if(!specP.getScoreboardTags().contains("spectating")){
									specP.kickPlayer(ChatColor.RED+"There is nobody online who you can spectate right now");
								}
							}}.runTaskLater(pl, 20*60);
						}
					}
					else{
						if(!specP.hasPermission("hardcore.spectator.bypass.slowness"))
							specP.setFlySpeed(FLY_SPEED);
						
						if(!specP.getScoreboardTags().contains("spectating")){
							specP.resetTitle();
							specP.addScoreboardTag("spectating");
							if(!specP.hasPermission("hardcore.spectator.bypass.blindness")){
								specP.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1000000, 0, true));
							}
						}
						if(!specP.hasPermission("hardcore.spectator.bypass.forcedtarget")){
							//sendSpectateNotice(specP, newTarget, specP.getLocation());//Done by TeleportListener below
							UUID targetUUID = newTarget.getUniqueId();
							if(specP.teleport(newTarget, TeleportCause.SPECTATE)){//TODO: remove in favor of below?
								//specP.setSpectatorTarget(newTarget);
								new BukkitRunnable(){@Override public void run(){
									specP.setSpectatorTarget(null);
									Player target = pl.getServer().getPlayer(targetUUID);
									specP.setSpectatorTarget(null);//TODO: remove?
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
						else if(!specP.hasPermission("hardcore.spectator.bypass.maxrange")){
							if(newTarget.getWorld().getUID().equals(specP.getWorld().getUID()) == false
									|| newTarget.getLocation().distanceSquared(specP.getLocation()) > 50*50){
								//sendSpectateNotice(specP, newTarget, specP.getLocation());//Done by TeleportListener below
								specP.teleport(newTarget, TeleportCause.SPECTATE);
							}
							else if(newTarget.getLocation().distanceSquared(specP.getLocation()) > 20*20){
								Vector fromSpecToTarget = newTarget.getLocation().toVector()
											.subtract(specP.getLocation().toVector());
								Vector bounceBackV = fromSpecToTarget.normalize();
								bounceBackV.multiply(new Vector(.8, 10, .8));
								specP.setVelocity(bounceBackV);
							}
							else if(!specP.hasPermission("hardcore.spectator.bypass.antixray") && canSeeThroughBlocks(specP.getEyeLocation())){
								Location closestAir = EvUtils.getClosestBlock(specP.getEyeLocation(), 10,
										(Block b) -> {return !b.getType().isOccluding();});
								if(closestAir != null){
									closestAir.add(.5, -1, .5);
									specP.teleport(closestAir, TeleportCause.CHORUS_FRUIT);
									specP.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*5, 0, true));
								}
								else specP.teleport(newTarget, TeleportCause.CHORUS_FRUIT);
							}
						}
					}
				}
			}
		}}.runTaskTimer(pl, 20, 20);//delay, freq
	}

	boolean notFar(Location a, Location b){
		return a.getWorld().getUID().equals(b.getWorld().getUID()) && a.distanceSquared(b) < 50*50;
	}

	/*@EventHandler
	public void onSpectatePlayer(PlayerSpectateEvent evt){
		//TODO: prevent spectators spectating spectators? (bypasses whitelist)
		//TODO: set specP inv contents <- target inv contents (empty if target == null)
	}*/
	private boolean isSpectating(Location spectatorLoc, Player target){
		return target != null && target.getWorld().getUID().equals(spectatorLoc.getWorld().getUID())
				&& target.getLocation().distanceSquared(spectatorLoc) < 50*50;
	}

	private HashMap<Pair<UUID, UUID>, BukkitTask> recentStartedSpectating = new HashMap<>();
	private void sendSpectatorNotices(Player spectator, Player newTarget, Location from, Location to){
		Pair<UUID, UUID> key = new Pair<>(spectator.getUniqueId(), newTarget == null ? null : newTarget.getUniqueId());
		BukkitTask oldTimeout = recentStartedSpectating.put(key,
			new BukkitRunnable(){@Override public void run(){
				recentStartedSpectating.remove(key);
				if(from != null){
					Player oldTarget = getClosestGm0WithPerms(from, /*spectator=*/spectator);
					if(oldTarget != null) sendSpectateEndNotice(spectator, oldTarget, /*leftTo=*/to);
				}
			}}.runTaskLater(pl, 20*5)//Must leave and stay gone for 5 seconds to send a "no longer spectating" notice.
		);
		// Cannot send a "is now spectating" notice if already sent (and hasn't sent "no longer spectating")
		if(oldTimeout == null) sendSpectateBeginNotice(spectator, newTarget, /*cameFrom=*/from);
		else oldTimeout.cancel();
	}
	@EventHandler(priority = EventPriority.LOWEST)
	public void onSpectateTeleport(PlayerTeleportEvent evt){
		if(!isSpectatorFavorYes(evt.getPlayer()) || evt.getPlayer().hasPermission("hardcore.spectator.bypass.tpcheck")) return;
		if(evt.getTo().getX() < -29990000 && evt.getTo().getZ() < -29990000){
			evt.getPlayer().sendMessage(ChatColor.RED+"You cannot join the archery event while in specator mode");
			evt.setCancelled(true);
			return;
		}
		if(evt.getCause() == TeleportCause.CHORUS_FRUIT || notFar(evt.getFrom(), evt.getTo())) return;
		Player newTarget = getClosestGm0WithPerms(evt.getTo(), /*spectator=*/evt.getPlayer());
		if(!isSpectating(evt.getTo(), newTarget)){
			evt.getPlayer().sendMessage(ChatColor.RED+"No valid/permitted target found at teleport destination");
			pl.getLogger().info("No target for "+evt.getPlayer()+" at destination: "+
					evt.getTo().getWorld().getName()+", "+
					evt.getTo().getBlockX()+", "+evt.getTo().getBlockY()+", "+evt.getTo().getBlockZ());
			evt.setCancelled(true);
			return;
		}
		sendSpectatorNotices(evt.getPlayer(), newTarget, evt.getFrom(), evt.getTo());

		//evt.getPlayer().setSpectatorTarget(p);
		//pl.getLogger().info(evt.getPlayer().getName()+" is now spectating "+target.getName());
	}

	public void addSpectator(Player player){
		if(spectators.add(player.getUniqueId())){
			pl.getLogger().info("Added spectator: "+player.getName());
			player.addScoreboardTag("spectating");
			if(!player.hasPermission("hardcore.spectator.bypass.blindness"))
				player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1000000, 0, true));
			if(!player.hasPermission("hardcore.spectator.bypass.slowness"))
				player.setFlySpeed(FLY_SPEED);
//			pl.setPermission(player, "essentials.tpa", false);
//			pl.setPermission(player, "essentials.tpahere", false);
//			pl.setPermission(player, "essentials.tpaccept", false);
//			player.getScoreboard().resetScores(player.getName());
			/*if(!player.hasPermission("hardcore.spectator.bypass.notify")){//Already handled by onJoin, onQuit, and onTeleport
				Player newTarget = getClosestGm0WithPerms(player.getLocation(), player);
				if(newTarget != null) newTarget.sendMessage("§7>> §b"+player.getDisplayName()+"§7 is now spectating you");
			}*/
			runSpecatorLoop();
		}
	}
	public boolean removeSpectator(Player player){
		pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Spectators").removeEntry(player.getName());
		//player.getPlayer().setFlySpeed(0.2f);
		if(spectators.remove(player.getUniqueId())){
			pl.getLogger().info("Removed spectator: "+player.getName());
			return true;
		}
		return false;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent evt){
		if(removeSpectator(evt.getPlayer()) && evt.getPlayer().getScoreboardTags().contains("dead")){
			Player oldTarget = getClosestGm0WithPerms(evt.getPlayer().getLocation(), /*spectator=*/evt.getPlayer());
			if(oldTarget != null){
				if(oldTarget != null) sendSpectateEndNotice(/*spectator=*/evt.getPlayer(), oldTarget, /*leftTo=*/null);
				sendSpectatorNotices(/*spectator=*/evt.getPlayer(), /*newTarget=*/null, /*from=*/evt.getPlayer().getLocation(), /*to=*/null);
			}
			evt.getPlayer().getScoreboard().resetScores(evt.getPlayer().getName());
			int ticksSinceDeath = evt.getPlayer().getStatistic(Statistic.TIME_SINCE_DEATH);
			long secondsSinceDeath = ticksSinceDeath/20;
			pl.getLogger().info("Ticks since death: "+ticksSinceDeath);
			pl.getLogger().info("Hours since death: "+(secondsSinceDeath/(60*60)));
			/*if(secondsSinceDeath >= SECONDS_UNTIL_RESPAWN){
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
			}*/
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
		if(isSpectator(evt.getPlayer())){
			addSpectator(evt.getPlayer());
			// Already done by runSpectatorLoop()
			//Player newTarget = getClosestGm0WithPerms(evt.getPlayer().getLocation(), /*spectator=*/evt.getPlayer());
			//if(newTarget != null) sendSpectatorNotices(/*spectator=*/evt.getPlayer(), newTarget, /*from=*/null, /*to=*/evt.getPlayer().getLocation());
		}
	}

	@EventHandler
	public void onGameModeChange(PlayerGameModeChangeEvent evt){
		if(evt.getNewGameMode() == GameMode.SPECTATOR){
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
			if(p != null && isSpectatorFavorYes(p)) addSpectator(p);
		}}.runTaskLater(pl, 20*5);
	}

	@EventHandler
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().charAt(0) != '/') return;
		String message = evt.getMessage().trim();
		String command = message.toLowerCase();
		int space = command.indexOf(' ');
		command = (space > 0 ? command.substring(1, space) : command.substring(1));
		Player player = evt.getPlayer();

		if(command.equals("tp") && message.indexOf(' ', space+1) == -1){
			if(SpectatorManager.isSpectator(player)){
				evt.setCancelled(true);
				Player target = null;
				if(space < 0 || (target=pl.getServer().getPlayer(message.substring(space + 1))) == null){
					player.sendMessage(ChatColor.RED+"Please specify who you wish to tp to (exact username)");
					player.sendMessage("Note: you can also use vanilla spectator menu (press 1)");
				}
				if(!player.isOp() && !SpectatorManager.canSpectate(player.getUniqueId(), target)){
					if(getSpectateMode(target) == WatchMode.WHITELIST)
						player.sendMessage(ChatColor.GRAY+target.getDisplayName()+
								ChatColor.RED+" has not added you to their spectator whitelist");
					else if(getSpectateMode(target) == WatchMode.BLACKLIST)
						player.sendMessage(ChatColor.GRAY+target.getDisplayName()+
								ChatColor.RED+" has you on their spectator blacklist");
				}
				else{
					player.teleport(target, TeleportCause.COMMAND);
					player.setSpectatorTarget(target);
				}
			}
		}
	}

	@EventHandler //TODO: isn't triggered by spectators opening their inventory???
	public void onInventoryOpen(InventoryOpenEvent evt){
		if(evt.getPlayer() instanceof Player == false) return;
		Player player = (Player)evt.getPlayer();
		Entity entity = player.getSpectatorTarget();
		//pl.getLogger().info("player who opened: "+evt.getPlayer().getName()+", spectate target: "+(entity == null ? "null" : entity.getName()));
		if(entity == null || entity instanceof Player == false || !SpectatorManager.isSpectator(player)) return;
		pl.getLogger().info("with spectator target");
		if(evt.getInventory().getType() == InventoryType.PLAYER){
			pl.getLogger().info("inv type = PLAYER");
			Player target = (Player)entity;
			player.getInventory().setContents(target.getInventory().getContents());
			player.getInventory().setArmorContents(target.getInventory().getArmorContents());
			player.getInventory().setItemInOffHand(target.getInventory().getItemInOffHand());
		}
		//TODO: crafting table inventory?
	}//*/
}