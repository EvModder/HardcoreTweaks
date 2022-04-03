package Hardcore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TextUtils;

public class NewPlayerManager implements Listener{
	final HCTweaks pl;
	final int PRE_GEN_SPAWNS, MAX_NEW_ACCS_ON_SAME_IP;
	final ItemStack GUIDE_BOOK;
	final Location WAITING_BOX;
	final String WORLD_NAME, SPAWN_MSG, RESPAWN_MSG;
	final List<Integer> spawnRegions;
	final List<ArrayDeque<Location>> pregenSpawnLocs;

	private String formatDistanceUnits(String msgWithDists){
		//replace "30000" with "30k" etc in msg.
		return msgWithDists
//				.replace("00000000", "00m")
				.replace("0000000", "0m")
				.replace("000000", "m")
				.replace("00000", "00k")
				.replace("0000", "0k")
				.replace("000", "k");
	}

	public NewPlayerManager(HCTweaks plugin){
		pl = plugin;
		//mobSpawnListeners = new HashMap<>();
		WORLD_NAME = pl.getConfig().getString("spawn-in-world", "Reliquist");
		World hardcoreWorld = pl.getServer().getWorld(WORLD_NAME);
		Block chestBlock = TextUtils.getLocationFromString(pl.getConfig().getString("guide-book-location", WORLD_NAME+",0,1,0")).getBlock();
		if(chestBlock.getState() instanceof BlockInventoryHolder) {
			Container chest = (Container)chestBlock.getState();
			ItemStack book = null;
			for(ItemStack item : chest.getInventory().getContents()){
				if(item != null && item.getType() == Material.WRITTEN_BOOK) {
					book = item;
					break;
				}
			}
			GUIDE_BOOK = book;
		}
		else GUIDE_BOOK = null;
		if(GUIDE_BOOK == null) pl.getLogger().warning("Unable to find starter book");

		WAITING_BOX = TextUtils.getLocationFromString(pl.getConfig().getString("spawn-pending-location", WORLD_NAME+",0.5,70,0.5"));
		spawnRegions = pl.getConfig().getIntegerList("spawn-regions");
		if(spawnRegions.isEmpty()){
			pl.getLogger().severe("Need to define new-player-spawn regions!");
			spawnRegions.add(0);
		}
		final WorldBorder border = hardcoreWorld.getWorldBorder();
		spawnRegions.add((int)border.getSize()/2);
		if(border.getCenter().getBlockX() != 0 || border.getCenter().getBlockZ() != 0){
			pl.getLogger().severe("spawnRegions will fail if the world border is not centered at 0 0");
		}
		pregenSpawnLocs = new ArrayList<>();
		for(int i=1; i<spawnRegions.size(); ++i) pregenSpawnLocs.add(new ArrayDeque<>());
		for(String str : FileIO.loadFile("pre-gen-spawns.txt", "0.5,75,0.5").split("\n")){
			Location loc = TextUtils.getLocationFromString(hardcoreWorld, str);
			if(loc != null){
				int dist = Math.max(loc.getBlockX(), loc.getBlockZ());
				for(int i=0; i<pregenSpawnLocs.size(); ++i){
					if(dist < spawnRegions.get(i+1)){
						pregenSpawnLocs.get(i).add(loc);
						break;
					}
				}
			}
		}
		PRE_GEN_SPAWNS = pl.getConfig().getInt("prepared-spawn-locations", 2);
		if(PRE_GEN_SPAWNS < 1) pl.getLogger().severe("Config setting prepared-spawn-locations cannot be less than 1");
		for(int i=0; i<pregenSpawnLocs.size(); ++i){
			if(pregenSpawnLocs.get(i).size() < PRE_GEN_SPAWNS){
				pl.getLogger().info("Pre-Generating " + (PRE_GEN_SPAWNS - pregenSpawnLocs.get(i).size()) + " spawnpoints... "
						+ formatDistanceUnits("("+spawnRegions.get(i)+"-"+spawnRegions.get(i+1)+")"));
				while(pregenSpawnLocs.get(i).size() < PRE_GEN_SPAWNS){
					pregenSpawnLocs.get(i).add(getRandomSpawnLoc(spawnRegions.get(i), spawnRegions.get(i+1)));
				}
				saveSpawnLocs();
			}
		}
		SPAWN_MSG = TextUtils.translateAlternateColorCodes('&',
				pl.getConfig().getString("spawn-welcome-message", "&bWelcome, &7%name%&b!"));
		RESPAWN_MSG = TextUtils.translateAlternateColorCodes('&',
				pl.getConfig().getString("respawn-welcome-message", "&bWelcome, &7%name%&b!"));
		MAX_NEW_ACCS_ON_SAME_IP = pl.getConfig().getInt("max-new-accounts-per-day-with-same-ip", 3);
	}

	private void saveSpawnLocs(){
		FileIO.saveFile("pre-gen-spawns.txt", StringUtils.join(
				pregenSpawnLocs.stream().flatMap(spawns -> spawns.stream())
				.map(loc -> loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ())
				.iterator(), '\n'));
	}

	private boolean hasNearbyLava(Location loc){
		for(int x=-15; x<=15; ++x) for(int y=-10; y<=10; ++y) for(int z=-15; z<=15; ++z){
			Block block = loc.clone().add(x, y, z).getBlock();
			if(block != null && block.getType() == Material.LAVA) return true;
		}
		return false;
	}
	private boolean isOverLiquid(Location loc){
		Block b = loc.getBlock();
		while(b != null && b.isEmpty() && b.isPassable() && !b.isLiquid()) b = b.getRelative(BlockFace.DOWN);
		return b.isLiquid();
	}
	// Warning: Very laggy!  Call asynchronously when possible
	private Location getRandomSpawnLoc(double minDist, double maxDist){
		final Random rand = new Random();
		final World world = pl.getServer().getWorld(WORLD_NAME);
		final WorldBorder border = world.getWorldBorder();
		final Location origin = border.getCenter();
//		pl.getLogger().info("border.getSize(): "+border.getSize());
		final int seaLevel = world.getSeaLevel();
		while(true){
			double x = rand.nextDouble()*(maxDist-minDist) + minDist;
			double z = rand.nextDouble()*maxDist;
			if(rand.nextBoolean()){double t=x; x=z; z=t;}

			x = ((long)x/16)*16 - 8;// Make sure we are in the center of a chunk
			z = ((long)z/16)*16 - 8;
			x = Math.floor(origin.getX() + x) + 0.5d;
			z = Math.floor(origin.getZ() + z) + 0.5d;
			if(rand.nextBoolean()) x = -x;
			if(rand.nextBoolean()) z = -z;

			Location loc = new Location(world, x, 300, z);
			if(!loc.getChunk().load(true)){
				pl.getLogger().severe("Failed to generate spawnLoc chunk!");
				return null;
			}

			while(loc.getBlockY() > seaLevel && (loc.getBlock() == null || loc.getBlock().isEmpty())) loc.setY(loc.getY() - 1);
			String debugStr = "Candidate X,Y,Z: "+loc.getBlockX()+" "+loc.getBlockY()+" "+loc.getBlockZ();

			if(loc.getY() < seaLevel) pl.getLogger().info(debugStr+" >> Below sea level (Y="+loc.getBlockY()+")");
			else if(isOverLiquid(loc)) pl.getLogger().info(debugStr+" >> Over liquid");
			else if(hasNearbyLava(loc)) pl.getLogger().info(debugStr+" >> Near to lava");
			else{
				pl.getLogger().info(debugStr+" >> SUCCESS");
				loc.setY(loc.getY() + 3.02); // Two blocks above the ground
				return loc;
			}
		}
	}

	private void refreshWaitRoomBox(Location loc, Player player){
		if(loc == null && player != null) loc = player.getLocation();
		// Fill +-3 x/y/z cube with Bedrock
		for(int x=-3; x<=3; ++x) for(int y=-3; y<=3; ++y) for(int z=-3; z<=3; ++z){
			Block block = loc.getBlock().getRelative(x, y, z);
			block.setType(Material.BEDROCK);
		}
		// Fill diamond-shaped (surrounding 6 sides) with EndGateways
		int[] xs = new int[]{2,0,0, -2,-0,-0,  1,1,0, -1,-1,-0,  +1,-1,+1, -1,+0,+0};
		int[] ys = new int[]{0,2,0, -0,-2,-0,  1,0,1, -1,-0,-1,  -1,+1,+0, +0,+1,-1};
		int[] zs = new int[]{0,0,2, -0,-0,-2,  0,1,1, -0,-1,-1,  +0,+0,-1, +1,-1,+1};
		for(int i=0; i<xs.length; ++i){
			Block block = loc.getBlock().getRelative(xs[i], ys[i], zs[i]);
			block.setType(Material.END_GATEWAY);
			EndGateway gatewayState = (EndGateway)block.getState();
			gatewayState.setAge(-2147483648);
			gatewayState.update(true);
		}
		// Fill the 6 faces with Barriers
		loc.getBlock().getRelative(BlockFace.UP).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.DOWN).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.NORTH).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.SOUTH).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.EAST).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.WEST).setType(Material.BARRIER);
		// Fill the block itself with Air
		loc.getBlock().setType(Material.AIR);
	}

	private void giveGuideBook(Player player){
		ItemStack myGuide = GUIDE_BOOK.clone();
		BookMeta meta = (BookMeta) myGuide.getItemMeta();
		List<String> lore = meta.getLore();
		lore.add(ChatColor.GRAY+"Owner: "+player.getName());
		String dateStr = new SimpleDateFormat("yyy-MM-dd").format(new Date());
		lore.add(ChatColor.GRAY+"Printed: "+dateStr);
		meta.setLore(lore);
		/*int idx = 0;
		for(String page : meta.getPages()){
			pl.getLogger().info("Page "+idx+": "+page);
			if(page.contains("%name%")) meta.setPage(++idx, page.replaceAll("%name%", player.getName()));
		}*/
		myGuide.setItemMeta(meta);
		player.getInventory().setItemInMainHand(myGuide);
	}

	private int getSpawnRegion(Player player){
		for(String tag : player.getScoreboardTags()){
			if(tag.startsWith("spawn_region_")) return Integer.parseInt(tag.substring(13));
		}
		return 0;
	}
	private void setSpawnRegion(Player player, int spawn_region){
		ArrayList<String> removeTags = new ArrayList<String>();
		for(String tag : player.getScoreboardTags()) if(tag.startsWith("spawn_region_")) removeTags.add(tag); 
		for(String tag : removeTags) player.removeScoreboardTag(tag);
		player.addScoreboardTag("spawn_region_"+spawn_region);
	}

	private void initPlayerTagsAndStats(Player player){
//		pl.getLogger().warning("Spawning new player: "+player.getName());
		player.addScoreboardTag("unconfirmed");

		// Register advancement-rank (in TAB) scoreboard
		String Adv0TeamName = ScoreboardManager.getAdvancementTeamName(0);
		Team newTeam = player.getScoreboard().getTeam(Adv0TeamName);
		if(newTeam == null) newTeam = player.getScoreboard().registerNewTeam(Adv0TeamName);
		newTeam.addEntry(player.getName());

		// Set some attributes
		player.setWalkSpeed(0f);
		player.setInvulnerable(true);
		player.setGravity(false);

		// Load carry-over stats from previous lives
		File deathDir = new File("./plugins/EvFolder/deaths/"+player.getName());
		if(deathDir.exists()){
			int numDeaths = deathDir.listFiles().length;
			pl.getLogger().info(player.getName()+" has "+numDeaths+" deaths");
			player.setStatistic(Statistic.DEATHS, numDeaths);
			String deathStats = FileIO.loadFile("deaths/"+player.getName()+"/death-stats.txt", "");
			for(String line : deathStats.split("\n")){
				int i = line.indexOf(',');
				if(i != -1){
					EntityType killedByEntity = EntityType.valueOf(line.substring(0, i).trim());
					int numTimesKilled = Integer.parseInt(line.substring(i+1).trim());
					player.setStatistic(Statistic.ENTITY_KILLED_BY, killedByEntity, numTimesKilled);
				}
			}
			new BukkitRunnable(){@Override public void run(){
				//player.setStatistic(Statistic.DEATHS, numDeaths);//TODO: is this line needed?
				ScoreboardManager.setNumDeaths(player, numDeaths);
			}}.runTaskLater(pl, 20);
			pl.getServer().broadcastMessage(RESPAWN_MSG.replaceAll("%name%", player.getName()));
			player.sendTitle(/*title=*/"", /*subtitle=*/"§7§lWelcome back to §6§lH§ea§6§lrd§ec§6§lore§7§l!",
					/*fadeIn=*/10, /*stay=*/20*15, /*fadeOut=*/20);
		}
		else{
			pl.getServer().broadcastMessage(SPAWN_MSG.replaceAll("%name%", player.getName()));
			player.sendTitle(/*title=*/"§7§lWelcome to §6§lH§ea§6§lrd§ec§6§lore§7§l!",
					/*subtitle=*/"§9Read the book to be set free and spawn in the world",
					/*fadeIn=*/10, /*stay=*/20*15, /*fadeOut=*/20);
		}
	}

	private void setPlayerFree(Player player){
		player.setWalkSpeed(0.2f);
		player.setInvulnerable(false);
		player.setGravity(true);
		player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 10, 3, true));
		player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10, 3, true));
		player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 2, 0, true));
		player.setSaturation(20);
		player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
		player.addScoreboardTag("joined");
		player.addScoreboardTag("autoquit_ping_3000");

		// Fetch a pregen'd spawn loc
		int spawn_region = getSpawnRegion(player);
		Location spawnLoc = pregenSpawnLocs.get(spawn_region).remove();
		saveSpawnLocs();
		if(pregenSpawnLocs.get(spawn_region).size() < PRE_GEN_SPAWNS){
			new BukkitRunnable(){@Override public void run(){
				pregenSpawnLocs.get(spawn_region).add(getRandomSpawnLoc(spawnRegions.get(spawn_region), spawnRegions.get(spawn_region+1)));
				saveSpawnLocs();
			}}.runTaskLater/*Asynchronously*/(pl, 20*60);//60s
		}
		pl.getLogger().warning("Spawning new player '"+player.getName()+"' at: "
				+ TextUtils.locationToString(spawnLoc, ChatColor.GREEN, ChatColor.YELLOW, 0));
		player.setBedSpawnLocation(spawnLoc);
		player.teleport(spawnLoc);
	}

	HashMap<String, Integer> newPlayerCountByIP = new HashMap<>();
	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent evt){
		final UUID uuid = evt.getPlayer().getUniqueId();
		OfflinePlayer offP = pl.getServer().getOfflinePlayer(uuid);
		if(!offP.hasPlayedBefore()){
			String playerIP = evt.getAddress().getHostAddress();
			int numNewPlayersOnIP = newPlayerCountByIP.getOrDefault(playerIP, 0) + 1;
			if(numNewPlayersOnIP > MAX_NEW_ACCS_ON_SAME_IP){
				evt.setKickMessage(ChatColor.RED+"Too many new players have joined from your IP recently..\nTry again tomorrow");
				evt.setResult(Result.KICK_OTHER);
			}
			else newPlayerCountByIP.put(playerIP, numNewPlayersOnIP);
		}
	}
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt){
		final Player player = evt.getPlayer();
		// Update first-(re)join tags & stats
		if(player.getScoreboardTags().isEmpty()) initPlayerTagsAndStats(player);

		// Unconfirmed player (re)joined
		if(player.getScoreboardTags().contains("unconfirmed")){
			player.sendMessage(ChatColor.GREEN+">> "+ChatColor.GOLD+ChatColor.BOLD+"Read the book to get started");
			player.getInventory().clear();
			giveGuideBook(player);
			for(Player p : player.getServer().getOnlinePlayers()) p.hidePlayer(pl, player);
			refreshWaitRoomBox(WAITING_BOX, player);
//			// TODO: It might be possible to get rid of this now
//			// Disable advancement announcement while teleporting player to waiting area with end gateways
//			final boolean announceAdvDefault = player.getWorld().getGameRuleDefault(GameRule.ANNOUNCE_ADVANCEMENTS);
//			player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
//			new BukkitRunnable(){@Override public void run(){
//				player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, announceAdvDefault);
//				Player player = pl.getServer().getPlayer(uuid);
//				if(player != null && player.getScoreboardTags().contains("unconfirmed")) player.teleport(WAITING_BOX);
//			}}.runTaskLater(pl, 20);
			player.teleport(WAITING_BOX);
		}

		// Update color_nick if a player changes their username
		if(player.getScoreboardTags().contains("color_nick")){
			new BukkitRunnable(){@Override public void run(){
				final String color = TextUtils.getCurrentColor(player.getDisplayName());
				if(color != null && !ChatColor.stripColor(player.getDisplayName()).equals(player.getName())){
					pl.getLogger().info("updating nickname: "+color.replace(""+ChatColor.COLOR_CHAR, "&")+player.getName());
					pl.runCommand("nick "+player.getName()+" "+color.replace(""+ChatColor.COLOR_CHAR, "&")+player.getName());
				}
				else pl.getLogger().info(player.getName()+" does not have a colored nick");
			}}.runTaskLater(pl, 40);
		}
	}

	List<String> unconfirmedCommands = Arrays.asList("/help", "/rules", "/ping", "/gc", "/ignore");
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		final String command = evt.getMessage().trim().toLowerCase();
		if(command.equals("/accept-terms") && evt.getPlayer().removeScoreboardTag("unconfirmed")){
			evt.setCancelled(true);
			//TODO: might be able to remove this
			// This is the silent way to remove this advancement (in case it was erroneously given)
			NamespacedKey key = NamespacedKey.minecraft("end/enter_end_gateway");
			AdvancementProgress progress = evt.getPlayer().getAdvancementProgress(pl.getServer().getAdvancement(key));
			for(String criteria : progress.getRemainingCriteria()) progress.revokeCriteria(criteria);
			// This way also works, but creates a console log
			//pl.runCommand("minecraft:advancement revoke "+evt.getPlayer().getName()+" only minecraft:end/enter_end_gateway");
			setPlayerFree(evt.getPlayer());
		}
		else if(evt.getPlayer().getScoreboardTags().contains("unconfirmed")){
			for(String allowedCmd : unconfirmedCommands) if(command.startsWith(allowedCmd)) return;

			if(command.matches("/spawn-region \\d")){
				final int spawn_region = Integer.parseInt(command.substring(14));
				final int minDist = spawnRegions.get(spawn_region);
				final int maxDist = spawnRegions.get(spawn_region+1);
				setSpawnRegion(evt.getPlayer(), spawn_region);
				final String updatedSpawnMsg = formatDistanceUnits("You will now spawn between "+minDist+" and "+maxDist);
				evt.getPlayer().sendMessage(ChatColor.GOLD+updatedSpawnMsg);
			}
			else{
				evt.getPlayer().sendMessage(ChatColor.RED+"You must read the book before you can do that");
			}
			evt.setCancelled(true);
		}
		else if(evt.getPlayer().getScoreboardTags().contains("dead") && command.equals("/respawn")){
			evt.setCancelled(true);
			long secondsLeft = HCTweaks.secondsLeftUntilRespawn(evt.getPlayer());
			if(secondsLeft > 0){
				evt.getPlayer().sendMessage(ChatColor.RED+"You cannot use that command yet");
				evt.getPlayer().sendMessage(SpectatorManager.formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY));
			}
			else pl.resetPlayer(evt.getPlayer());
		}
	}
}