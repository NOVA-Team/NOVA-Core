package nova.core.util;

import se.jbee.inject.bind.BinderModule;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Calclavia
 */
public abstract class Manager<T extends Buildable> {
	private final Registry<Factory<T> > registry;

	public Manager(Registry<Factory<T>> registry) {
		this.registry = registry;
	}

	public final List<Factory<T>> register(Class<? extends T> registerType) {
		@SuppressWarnings("unchecked")
		Set<Factory<T>> set = Buildable.factoriesFor((Class<T>) registerType);
		return set.stream().map(this::register).collect(Collectors.toList());
	}

	public final Factory<T> register(Factory<T> factory) {
		factory = beforeRegister(factory);
		registry.register(factory);
		return factory;
	}

	protected Factory<T> beforeRegister(Factory<T> factory) {
		return factory;
	}

	public Optional<T> make(String name) {
		Optional<Factory<T>> factory = getFactory(name);
		return factory.map(f -> f.make());
	}

	public Optional<T> make(String name, Object ... instanceArguments) {
		Optional<Factory<T>> factory = getFactory(name);
		return factory.map(f -> f.make(instanceArguments));
	}

	public Optional<Factory<T>> getFactory(String name) {
		return registry.get(name);
	}

	public boolean contains(String name) {
		return registry.contains(name);
	}

	public Set<Factory<T>> all() {
		return registry.values();
	}

	public static class ManagerModule extends BinderModule {

		@Override
		protected void declare() {
			//TODO add Factories to DI
		}
	}
}
