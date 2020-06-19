package Hardcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import Hardcore.HCTweaks;
import java.util.List;
import net.evmodder.EvLib.EvCommand;

public class CommandRegionCleanup extends EvCommand{
	HCTweaks pl;
	public CommandRegionCleanup(HCTweaks pl){super(pl); this.pl = pl;}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		return null;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		//cmd: /regioncleanup [world/all] [min dist from 0,0]m [max file size]kb [min time-since-modified]d [max age-vs-modified]d
		sender.sendMessage(ChatColor.RED+"reee not yet implemented");
		return false;
/*
		// reset variables
		long minDistFromSpawn = -1, maxFileSize= -1, minTimeSinceModified = -1, maxFileModifiedGapInMillis = -1;
		World world = (sender instanceof Player) ? ((Player)sender).getWorld() : null;

		// Read args
		for(String arg : args){
			arg = arg.toLowerCase();
			if(arg.equals("confirm")) continue;
			if(pl.getServer().getWorld(arg) != null) world = pl.getServer().getWorld(arg);
			else if(arg.equals("all")) world = null;
			else if(arg.endsWith("kb")){
				if(maxFileSize == -1) maxFileSize = Long.parseLong(arg.substring(0, arg.length()-2));
				else{sender.sendMessage(ChatColor.RED+"Ambiguous filesize (kb) argument: "+arg); return false;}
			}
			else if(arg.endsWith("m") || arg.endsWith("b")){
				if(minDistFromSpawn == -1) minDistFromSpawn = Long.parseLong(arg.substring(0, arg.length()-1))/512;
				else{sender.sendMessage(ChatColor.RED+"Ambiguous distance (blocks) argument: "+arg); return false;}
			}
			else if(arg.endsWith("r")){
				if(minDistFromSpawn == -1) minDistFromSpawn = Long.parseLong(arg.substring(0, arg.length()-1));
				else{sender.sendMessage(ChatColor.RED+"Ambiguous distance (regions) argument: "+arg); return false;}
			}
			else if(arg.endsWith("d")){
				if(minTimeSinceModified == -1)
					minTimeSinceModified = Long.parseLong(arg.substring(0, arg.length()-1))*DAYS_TO_MILLIS;
				else if(maxFileModifiedGapInMillis == -1)
					maxFileModifiedGapInMillis = Long.parseLong(arg.substring(0, arg.length()-1))*DAYS_TO_MILLIS;
				else{sender.sendMessage(ChatColor.RED+"Ambiguous file age (days) argument: "+arg); return false;}
			}
		}
		
		if(minDistFromSpawn < 0){
			sender.sendMessage(ChatColor.RED+"Must specify a minimum distance from spawn for\n" +
							   ChatColor.RED+"regions to qualify for deletion (0 for no limit)");
			return false;
		}
		if(world == null) world = pl.getServer().getWorlds().get(0);

		HashMap<Long, Integer> szOccurCount = new HashMap<Long, Integer>();
		ArrayList<Long> fileAges = new ArrayList<Long>();
		for(File file : new File(EvUtils.getRegionFolder(world)).listFiles()){
			if(file.isFile() && file.getName().endsWith(".mca")){
				long fileSize = roundToSigFigs(file.length()/1024, 3);
				//pl.getLogger().info("sigFig fileSize: "+fileSize+" (regular size: "+(file.length()/1024)+")");
				Integer currentCount = szOccurCount.get(fileSize);
				szOccurCount.put(fileSize, currentCount == null ? 1 : currentCount+1);
				fileAges.add(file.lastModified());
			}
		}
		long mostOccurances = 0, fileSizeMode = -1;
		for(Long fileSz : szOccurCount.keySet()){
			//pl.getLogger().info(szOccurCount.get(fileSz)+" occurances of "+fileSz);
			if(szOccurCount.get(fileSz) > mostOccurances){
				fileSizeMode = fileSz;
				mostOccurances = szOccurCount.get(fileSz);
			}
		}
		sender.sendMessage("File size mode: "+fileSizeMode);
		Collections.sort(fileAges);
		long fileAgeMedian = fileAges.get(fileAges.size()/2);
		sender.sendMessage("Median file age: "+
				new SimpleDateFormat("yyyy-MM-dd").format(new Date(fileAgeMedian)));

		// If maxFileSize isn't given, calculate (and use) the Mode
		if(maxFileSize == -1) maxFileSize = fileSizeMode;
		// If minFileAge isn't given, calculate (and use) the Median
		if(minTimeSinceModified == -1) minTimeSinceModified = System.currentTimeMillis() - fileAgeMedian;
		// If maxFileModifiedGap isn't given, use 1day
		if(maxFileModifiedGapInMillis == -1) maxFileModifiedGapInMillis = DAYS_TO_MILLIS;

		final long MIN_DIST_IN_R = minDistFromSpawn;
		final long MAX_FILE_SZ = maxFileSize;
		final long MAX_FILE_AGE = System.currentTimeMillis() - minTimeSinceModified;
		final long MAX_AGE_VS_CREATION = maxFileModifiedGapInMillis;
		final List<World> worlds = (world == null ? pl.getServer().getWorlds() : Arrays.asList(world));

		if(!args[args.length-1].equals("confirm")){
			//TODO: remove these quantiles?
			new Thread(){@Override public void run(){
			for(World w : worlds){
				File folder = new File("./"+w.getName()+"/region/");
				if(!folder.exists()) continue;
				pl.getLogger().info("Filesize quantiles for "+w.getName()+":");
				File[] files = folder.listFiles();
				long[] fileSizes = new long[folder.listFiles().length];
				for(int i=0; i<files.length; ++i) fileSizes[i] = files[i].length()/1024;
				Arrays.sort(fileSizes);
				pl.getLogger().info("Min: "+fileSizes[0]);
				pl.getLogger().info("10%: "+fileSizes[fileSizes.length*1/10 - 1]);
				pl.getLogger().info("20%: "+fileSizes[fileSizes.length*2/10 - 1]);
				pl.getLogger().info("30%: "+fileSizes[fileSizes.length*3/10 - 1]);
				pl.getLogger().info("40%: "+fileSizes[fileSizes.length*4/10 - 1]);
				sender.sendMessage("50%: "+fileSizes[fileSizes.length*5/10 - 1]+" kb");
				pl.getLogger().info("60%: "+fileSizes[fileSizes.length*6/10 - 1]);
				pl.getLogger().info("70%: "+fileSizes[fileSizes.length*7/10 - 1]);
				pl.getLogger().info("80%: "+fileSizes[fileSizes.length*8/10 - 1]);
				pl.getLogger().info("90%: "+fileSizes[fileSizes.length*9/10 - 1]);
				pl.getLogger().info("Max: "+fileSizes[fileSizes.length - 1]);
			}
			}}.start();
			if(sender instanceof Player){
				StringBuilder commandString = new StringBuilder('/').append(command.getName());
				for(String str : args) commandString.append(' ').append(str);

				//-----------------------------------------------------------
				//sender.sendMessage("\n \n \n \n \n \n \n \n \n \n");
				sender.sendMessage("§c§l§m====================================");
				Utils.sendHyperMsg("§6Click to confirm: §7[", "§f>>§cDelete§f<<§7]",
						"/"+commandString.toString()+" confirm", null, (Player)sender);
				sender.sendMessage("§c§l§m====================================");
				//-----------------------------------------------------------
				Date fileAgeCutoff = new java.util.Date(MAX_FILE_AGE);
				DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
				sender.sendMessage(
						"Delete §7region .mca"+msgC+" files IF:\n" +
						"At least §c"+MIN_DIST_IN_R+msgC+"r (§c"+(MIN_DIST_IN_R*512)+msgC+" blocks) from x=0,z=0\n" +
						"File is no larger then §c"+MAX_FILE_SZ+msgC+"KB\n" +
						"File hasn't been modified since §c"+dateFormat.format(fileAgeCutoff)+msgC+"\n" +
						"File 'last-modified' vs 'creation' < §c"
							  +(MAX_AGE_VS_CREATION/DAYS_TO_MILLIS)+msgC+" days apart\n" +
						"In world: §e"+(world == null ? "§call" : world.getName())+msgC+".\n"
							  +"§4[§c§lWarning§4] §6This may delete a lot of your world. To undo, read\n"
							  + "This page:§7§o http://bukkit.org/reducelag/put-page-here \n ");
			}
			else{
				sender.sendMessage(ChatColor.RED+"Add 'confirm' to the command to run the region delete");
			}
			return true;
		}

		sender.sendMessage(prefix+"§6Staring region-delete...");
		currentSender = sender instanceof Player ? ((Player)sender).getUniqueId() : null;

		new Thread(){@Override public void run(){
			String todaysDate = new SimpleDateFormat("MM-dd-yyyy").format(new Date());
			File delFolder = new File("./pls/EvFolder/DELETED/regions "+todaysDate);
			if(!delFolder.mkdir()){
				new File("./pls/EvFolder/DELETED").mkdir();
				delFolder.mkdir();
			}

			int skipCuzSize = 0, skipCuzAge = 0, skipCuzAgeVsCreate = 0, skipCuzError = 0, skipCuzDist = 0;
			for(World w : worlds){
				File folder = new File("./"+w.getName()+"/region/");
				if(!folder.exists()) continue;
				long deletedKB = 0, numDeleted = 0;

				for(File file : folder.listFiles()){
					if(file.isFile() && file.getName().endsWith(".mca")){
						long fileSize = file.length()/1024;
						if(fileSize > MAX_FILE_SZ){
							++skipCuzSize; continue;//<<<<<<<<<<<<<<<
						}
						long lastModified = file.lastModified();
						if(lastModified > MAX_FILE_AGE){
							++skipCuzAge; continue;//<<<<<<<<<<<<<<<
						}
						try{
							BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
							long creationTime = attr.creationTime().toMillis();
							if(creationTime == lastModified) creationTime = 0;// If equal, REAL creation time is unknown :(
							if(lastModified - creationTime > MAX_AGE_VS_CREATION){
								++skipCuzAgeVsCreate; continue;//<<<<<<<<<<<<<<<
							}
						}
						catch(IOException e){
							pl.getLogger().warning("Unable to read file attributes for "+file.getName());
							++skipCuzError; continue;//<<<<<<<<<<<<<<<
						}
						

						String fname = file.getName();
						long x = Math.abs(Integer.parseInt(fname.split("\\.")[1]));
						long z = Math.abs(Integer.parseInt(fname.split("\\.")[2]));
//						//Useful (but not necessary) info
//						int blockX = x*512;//= world.getChunkAt(x*32, z*32).getBlock(0, 0, 0).getX();
//						int blockZ = z*512;//= world.getChunkAt(x*32, z*32).getBlock(0, 0, 0).getZ();
						if(x < MIN_DIST_IN_R || z < MIN_DIST_IN_R){
							++skipCuzDist; continue;//<<<<<<<<<<<<<<<
						}

						// Perform delete
						file.renameTo(new File(delFolder.getPath()+'/'+file.getName()));
						deletedKB += fileSize;
						++numDeleted;
					}
				}
				//int skipCuzSize = 0, skipCuzAge = 0, skipCuzAgeVsCreate = 0, skipCuzError = 0, skipCuzDist = 0;
				CommandSender sender = (currentSender != null) ?
						pl.getServer().getPlayer(currentSender) : null;
				if(sender == null) pl.getServer().getConsoleSender();
				sender.sendMessage("§6Finished reg-del for §c"+w.getName()+"§6.");
				sender.sendMessage("§6Space freed: §c"+deletedKB+"§6KB, §c"+numDeleted+"§6 files");
			}
			sender.sendMessage("§aProcess Complete!");
			sender.sendMessage("Skipped file reasons: "
					+"FileSize:"+skipCuzSize+", FileAge:"+skipCuzAge+", CreationVsModified:"+skipCuzAgeVsCreate
					+", DistFromSpawn:"+skipCuzDist+", Error:"+skipCuzError);
			currentSender = null;
		}}.start();

		return true;*/
	}
}