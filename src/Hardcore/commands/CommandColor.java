package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.HCTweaks;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.TextUtils;

public class CommandColor extends EvCommand{
	public CommandColor(HCTweaks pl){super(pl);}
	static final char COLOR_SYMBOL = ChatColor.WHITE.toString().charAt(0);

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		if(args.length > 0){
			ArrayList<String> tabCompletes = new ArrayList<String>();
			for(char ch : TextUtils.COLOR_CHARS) tabCompletes.add(String.valueOf(ch));
			return tabCompletes;
		}
		return null;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}
		String displayName = ((Player)sender).getDisplayName();
		int nameIdx = displayName.indexOf(sender.getName());
		if(nameIdx == -1){
			sender.sendMessage(ChatColor.RED+"You cannot use this command if you have a custom nickname set");
			return true;
		}
		if(args.length == 0){
			sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
					"&00 &11 &22 &33 &44 &55 &66 &77 &88 &99 &aa &bb &cc &dd &ee &ff"));
			sender.sendMessage(ChatColor.GRAY+"/color #");
			return true;
		}
		if(args.length > 1 || (args[0].replaceAll("&", "")).length() > 1){
			String colorNick = TextUtils.translateAlternateColorCodes('&', args[0]);
			String rawNick = ChatColor.stripColor(colorNick);
			if(!sender.hasPermission("hardcore.color.custom") || !rawNick.equalsIgnoreCase(displayName)){
				sender.sendMessage(ChatColor.GRAY+"Please provide just a single character");
			}
			else if(!rawNick.equals(displayName)){
				sender.sendMessage(ChatColor.GRAY+"Please use your exact name (case sensitive)");
			}
			else{
				if(!sender.hasPermission("hardcore.color.formats")) args[0] = TextUtils.stripFormatsOnly(args[0], '&');
				HCTweaks.getPlugin().runCommand("nick "+sender.getName()+" "+args[0]);
				sender.sendMessage(ChatColor.GREEN+"Color set!");
				((Player)sender).addScoreboardTag("color_nick");
			}
			return true;
		}
		char colorCh = Character.toLowerCase(args[0].charAt(0));
		ChatColor color = ChatColor.getByChar(colorCh);
		if(color == null){
			sender.sendMessage(ChatColor.GRAY+"Unknown color '"+colorCh+"'");
			return true;
		}
		if(TextUtils.isFormat(colorCh) && !sender.hasPermission("hardcore.color.formats")){
			sender.sendMessage(ChatColor.GRAY+"Please pick a color code");
			return true;
		}

		// Got to here, update their nickname!
		int cutBeforeIdx = nameIdx;
		while(cutBeforeIdx > 1 && displayName.charAt(cutBeforeIdx-2) == COLOR_SYMBOL) cutBeforeIdx -= 2;
		String textBeforeName = displayName.substring(0, cutBeforeIdx).replace(COLOR_SYMBOL, '&');
		String textAfterName = displayName.substring(nameIdx+sender.getName().length()).replace(COLOR_SYMBOL, '&');

		if(colorCh == 'f' || colorCh == 'r'){
			if(textBeforeName.isEmpty() && textAfterName.isEmpty()) HCTweaks.getPlugin().runCommand("nick "+sender.getName()+" off");
			else HCTweaks.getPlugin().runCommand("nick "+sender.getName()+" "+textBeforeName+sender.getName()+textAfterName);
			((Player)sender).removeScoreboardTag("color_nick");
		}
		else{
			HCTweaks.getPlugin().runCommand("nick "+sender.getName()+" "+textBeforeName+("&"+colorCh)+sender.getName()+textAfterName);
			sender.sendMessage(color+"Color set!");
			((Player)sender).addScoreboardTag("color_nick");
		}
		return true;
	}
}