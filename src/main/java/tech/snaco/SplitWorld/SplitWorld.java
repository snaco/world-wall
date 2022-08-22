package tech.snaco.SplitWorld;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

public class SplitWorld implements ModInitializer {
  public static final Logger LOGGER = LoggerFactory.getLogger("modid");
  Type ITEM_STACK_LIST_TYPE = new TypeToken<ArrayList<ItemStack>>() {
  }.getType();
  Gson gson = new Gson();

  @Override
  public void onInitialize() {
    ServerTickEvents.START_WORLD_TICK.register((world) -> {
      var players = world.getPlayers();
      for (var player : players) {
        initializePlayerDir(player);
        var pos = player.getPos();
        if (pos.x < 0) {
          setGameMode(player, GameMode.CREATIVE);
        }
        if (pos.x > 0) {
          setGameMode(player, GameMode.SURVIVAL);
        }
      }
    });
  }

  private void initializePlayerDir(ServerPlayerEntity player) {
    var dirName = getDir(player);
    var playerDir = Paths.get(dirName);
    try {
      var playerDirPath = Files.createDirectory(playerDir);
      if (Files.notExists(playerDirPath) && Files.exists(playerDirPath)) {
        new File(dirName).mkdir();
      }
    } catch (FileAlreadyExistsException ex) {
      // fine
    } catch (IOException ex) {
      LOGGER.error("Error initializing player dir!", ex);
    }
  }

  private boolean setGameMode(ServerPlayerEntity player, GameMode targetGameMode) {
    if (player.isCreative() && targetGameMode == GameMode.SURVIVAL) {
      // save creative from player
      saveInventoryToFiles(player, GameMode.CREATIVE);

      // load survival
      loadInventoryFromDir(player, targetGameMode);

    } else if (!player.isCreative() && targetGameMode == GameMode.CREATIVE) {
      // save survival inventory
      saveInventoryToFiles(player, GameMode.SURVIVAL);

      // load creative inventory
      loadInventoryFromDir(player, targetGameMode);
    }
    player.changeGameMode(targetGameMode);
    return true;
  }

  private String getDir(ServerPlayerEntity player) {
    return String.format("%s_inv", player.getUuidAsString());
  }

  private void loadInventoryFromDir(ServerPlayerEntity player, GameMode gameMode) {
    try {
      NbtList nbtList = new NbtList();
      var dir = new File(getDir(player));
      var files = dir.listFiles();
      if (files == null) {
        player.getInventory().clear();
      } else {
        for (var file : dir.listFiles()) {
          if (file.getName().contains(gameMode.toString())) {
            var nbt = NbtIo.read(file);
            if (nbt != null) {
              nbtList.add(nbt);
            }
          }
        }
      }
      player.getInventory().clear();
      player.getInventory().readNbt(nbtList);
    } catch (FileNotFoundException ex) {
      LOGGER.error("Error reading inventory file!", ex);
      player.getInventory().dropAll();
    } catch (IOException ex) {
      LOGGER.error("Error reading inventory file!", ex);
      player.getInventory().dropAll();
    }
  }

  private void saveInventoryToFiles(ServerPlayerEntity player, GameMode gameMode) {
    try {
      var dir = getDir(player);
      var nbtList = new NbtList();
      nbtList = player.getInventory().writeNbt(nbtList);
      for (int i = 0; i < nbtList.size(); i++) {
        var nbt = nbtList.get(i);
        var file = new File(String.format("%s/%s_%d.nbt", dir, gameMode.toString(), i));
        NbtIo.write((NbtCompound) nbt, file);
      }
    } catch (Exception ex) {
      System.err.println("Error setting inventory file contents!");
    }
  }
}
