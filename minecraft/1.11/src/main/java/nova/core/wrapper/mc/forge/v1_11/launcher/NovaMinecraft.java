/*
 * Copyright (c) 2015 NOVA, All rights reserved.
 * This library is free software, licensed under GNU Lesser General Public License version 3
 *
 * This file is part of NOVA.
 *
 * NOVA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NOVA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NOVA.  If not, see <http://www.gnu.org/licenses/>.
 */

package nova.core.wrapper.mc.forge.v1_11.launcher;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.Metadata;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.ProgressManager.ProgressBar;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import nova.core.deps.MavenDependency;
import nova.core.event.ServerEvent;
import nova.core.wrapper.mc.forge.v1_11.NovaMinecraftPreloader;
import nova.core.wrapper.mc.forge.v1_11.depmodules.ClientModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.ComponentModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.GameInfoModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.KeyModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.LanguageModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.NetworkModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.RenderModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.SaveModule;
import nova.core.wrapper.mc.forge.v1_11.depmodules.TickerModule;
import nova.core.wrapper.mc.forge.v1_11.recipes.MinecraftRecipeRegistry;
import nova.core.wrapper.mc.forge.v1_11.wrapper.DirectionConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.VectorConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.block.BlockConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.block.world.WorldConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.cuboid.CuboidConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.data.DataWrapper;
import nova.core.wrapper.mc.forge.v1_11.wrapper.entity.EntityConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.inventory.InventoryConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.item.ItemConverter;
import nova.core.wrapper.mc.forge.v1_11.wrapper.item.OreDictionaryIntegration;
import nova.internal.core.Game;
import nova.internal.core.bootstrap.DependencyInjectionEntryPoint;
import nova.internal.core.deps.DepDownloader;
import nova.internal.core.launch.InitializationException;
import nova.internal.core.launch.NovaLauncher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The main Nova Minecraft Wrapper loader, using Minecraft Forge.
 * @author Calclavia
 */
@Mod(modid = NovaMinecraft.id, name = NovaMinecraft.name, version = NovaMinecraftPreloader.version, acceptableRemoteVersions = "*")
public class NovaMinecraft {

	public static final String id = "nova";
	public static final String name = "NOVA";
	public static final String mcId = "minecraft";

	@SidedProxy(clientSide = "nova.core.wrapper.mc.forge.v1_11.launcher.ClientProxy", serverSide = "nova.core.wrapper.mc.forge.v1_11.launcher.CommonProxy")
	public static CommonProxy proxy;
	@Mod.Instance(id)
	public static NovaMinecraft instance;
	private static NovaLauncher launcher;
	@Metadata(id)
	private static ModMetadata modMetadata;

	private static Set<ForgeLoadable> nativeConverters;
	private static Set<ForgeLoadable> novaWrappers = new HashSet<>();
	private static List<ForgeLoadable> novaModWrappers;

	public static void registerWrapper(ForgeLoadable wrapper) {
		novaWrappers.add(wrapper);
	}

	/**
	 * ORDER OF LOADING.
	 *
	 * 1. Native Loaders 2. Native Converters 3. Mods
	 */
	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent evt) {
		try {
			/**
			 * Search through all classes withPriority @NovaMod
			 */
			DependencyInjectionEntryPoint diep = new DependencyInjectionEntryPoint();
			diep.install(NetworkModule.class);
			diep.install(SaveModule.class);
			diep.install(TickerModule.class);
			diep.install(LanguageModule.class);
			diep.install(KeyModule.class);
			diep.install(ClientModule.class);
			diep.install(GameInfoModule.class);
			diep.install(RenderModule.class);
			diep.install(ComponentModule.class);

			Set<Class<?>> modClasses = NovaMinecraftPreloader.modClasses;

			proxy.registerResourcePacks(modClasses);
			launcher = new NovaLauncher(diep, modClasses);

			Game.inject(diep);

			/**
			 * Register native converters
			 */
			Game.natives().registerConverter(new DataWrapper());
			Game.natives().registerConverter(new EntityConverter());
			Game.natives().registerConverter(new BlockConverter());
			Game.natives().registerConverter(new ItemConverter());
			Game.natives().registerConverter(new WorldConverter());
			Game.natives().registerConverter(new CuboidConverter());
			Game.natives().registerConverter(new InventoryConverter());
			Game.natives().registerConverter(new VectorConverter());
			Game.natives().registerConverter(new DirectionConverter());

			/**
			 * Initiate recipe and ore dictionary integration
			 */
			OreDictionaryIntegration.instance.registerOreDictionary();
			MinecraftRecipeRegistry.instance.registerRecipes();

			/**
			 * Download dependencies
			 */
			launcher.generateDependencies();

			try {
				for (List<MavenDependency> dependencies : launcher.getNeededDeps().values()) {
					for (MavenDependency dep : dependencies) {
						DepDownloader.downloadDepdency(dep.getDownloadURL(), FMLInjectionData.data()[6] + "/mods/" + dep.getPath());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			ProgressBar progressBar = ProgressManager.push("Loading NOVA mods", modClasses.size(), true);
			launcher.load(new FMLProgressBar(progressBar));
			ProgressManager.pop(progressBar);

			/**
			 * Instantiate native loaders
			 */
			nativeConverters = Game.natives().getNativeConverters().stream().filter(n -> n instanceof ForgeLoadable).map(n -> (ForgeLoadable) n).collect(Collectors.toSet());
			nativeConverters.stream().forEachOrdered(loadable -> loadable.preInit(evt));

			// Initiate config system TODO: Storables
			//		launcher.getLoadedModMap().forEach((mod, loader) -> {
			//			Configuration config = new Configuration(new File(evt.getModConfigurationDirectory(), mod.name()));
			//			ConfigManager.instance.sync(config, loader.getClass().getPackage().getName());
			//		});

			Game.language().init();
			Game.render().init();
			Game.blocks().init();
			Game.items().init();
			Game.entities().init();

			//Load preInit
			int size = novaModWrappers.size() + novaWrappers.size();
			if (size > 0) {
				progressBar = ProgressManager.push("Pre-initializing NOVA wrappers", size);
				FMLProgressBar fmlProgressBar = new FMLProgressBar(progressBar);
				novaModWrappers.stream().forEachOrdered(wrapper -> {
					fmlProgressBar.step(wrapper.getClass());
					wrapper.preInit(evt);
				});
				novaWrappers.stream().forEachOrdered(wrapper -> {
					fmlProgressBar.step(wrapper.getClass());
					wrapper.preInit(evt);
				});
				fmlProgressBar.finish();
				ProgressManager.pop(progressBar);
			}

			proxy.preInit(evt);

			/**
			 * Register event handlers
			 */
			MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());
			FMLCommonHandler.instance().bus().register(new FMLEventHandler());
			MinecraftForge.EVENT_BUS.register(Game.retention());
		} catch (Exception e) {
			System.out.println("Error during preInit");
			e.printStackTrace();
			throw new InitializationException(e);
		}
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent evt) {
		try {
			proxy.init(evt);
			nativeConverters.stream().forEachOrdered(forgeLoadable -> forgeLoadable.init(evt));
			int size = novaModWrappers.size() + novaWrappers.size();
			if (size > 0) {
				ProgressManager.ProgressBar progressBar = ProgressManager.push("Initializing NOVA wrappers", size);
				FMLProgressBar fmlProgressBar = new FMLProgressBar(progressBar);
				novaModWrappers.stream().forEachOrdered(wrapper -> {
					fmlProgressBar.step(wrapper.getClass());
					wrapper.init(evt);
				});
				novaWrappers.stream().forEachOrdered(wrapper -> {
					fmlProgressBar.step(wrapper.getClass());
					wrapper.init(evt);
				});
				fmlProgressBar.finish();
				ProgressManager.pop(progressBar);
			}
		} catch (Exception e) {
			System.out.println("Error during init");
			e.printStackTrace();
			throw new InitializationException(e);
		}
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent evt) {
		try {
			proxy.postInit(evt);
			nativeConverters.stream().forEachOrdered(forgeLoadable -> forgeLoadable.postInit(evt));
			Game.recipes().init();
			int size = novaModWrappers.size() + novaWrappers.size();
			if (size > 0) {
				ProgressManager.ProgressBar progressBar = ProgressManager.push("Post-initializing NOVA wrappers", size);
				FMLProgressBar fmlProgressBar = new FMLProgressBar(progressBar);
				novaModWrappers.stream().forEachOrdered(wrapper -> {
					fmlProgressBar.step(wrapper.getClass());
					wrapper.postInit(evt);
				});
				novaWrappers.stream().forEachOrdered(wrapper -> {
					fmlProgressBar.step(wrapper.getClass());
					wrapper.postInit(evt);
				});
				fmlProgressBar.finish();
				ProgressManager.pop(progressBar);
			}
		} catch (Exception e) {
			System.out.println("Error during postInit");
			e.printStackTrace();
			throw new InitializationException(e);
		}
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		Game.events().publish(new ServerEvent.Start());
	}

	@Mod.EventHandler
	public void serverStopping(FMLServerStoppingEvent event) {
		Game.events().publish(new ServerEvent.Stop());
	}
}
