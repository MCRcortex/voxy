package me.cortex.voxy.client.config;

import com.mojang.datafixers.types.Func;
import me.cortex.voxy.common.util.Pair;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.*;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

public class SodiumConfigBuilder {

    private static class Enabler {
        public final Predicate<ConfigState> tester;
        public final ResourceLocation[] dependencies;
        public final boolean joinParent;
        public Enabler inheritedEnabler;
        public Enabler baseEnabler;
        public Enabler(Predicate<ConfigState> tester, ResourceLocation[] dependencies, boolean joinParent) {
            this.tester = tester;
            this.dependencies = dependencies;
            this.joinParent = joinParent;
        }
        public Enabler(Predicate<ConfigState> tester, String[] dependencies) {
            this(tester, dependencies, false);
        }
        public Enabler(Predicate<ConfigState> tester, ResourceLocation[] dependencies) {
            this(tester, dependencies, false);
        }
        public Enabler(Predicate<ConfigState> tester, String[] dependencies, boolean joinParent) {
            this(tester, mapIds(dependencies), joinParent);
        }

        /*
        public static Enabler joinAnd(Enabler... enablers) {
            Set<ResourceLocation> identifiers = new HashSet<>();
            for (var e : enablers) {
                for (var i : e.dependencies) {
                    identifiers.add(i);
                }
            }
            Predicate<ConfigState> tester = state->{
                for (var test:enablers) {
                    if (!test.tester.test(state)) {
                        return false;
                    }
                }
                return true;
            };
            var newEnabler = new Enabler(tester, identifiers.toArray(ResourceLocation[]::new));
            return newEnabler;
        }*/
        public Enabler joinAnd(Enabler parent) {
            Set<ResourceLocation> identifiers = new HashSet<>();
            for (var i : this.dependencies) {
                identifiers.add(i);
            }
            for (var i : parent.dependencies) {
                identifiers.add(i);
            }
            Predicate<ConfigState> tester = state->{
                if (!this.tester.test(state)) {
                    return false;
                }
                if (!parent.tester.test(state)) {
                    return false;
                }
                return true;
            };
            var newEnabler = new Enabler(tester, identifiers.toArray(ResourceLocation[]::new));
            newEnabler.baseEnabler = this;
            newEnabler.inheritedEnabler = parent;
            return newEnabler;
        }
    }

    public abstract static class Enableable <TYPE extends Enableable<TYPE>> {
        private @Nullable Enabler prevEnabler;
        protected @Nullable Enabler enabler;

        private TYPE setEnabler0(Enabler enabler) {
            this.prevEnabler = this.enabler;
            this.enabler = enabler;
            this.updateChildren();
            return (TYPE) this;
        }
        private void updateChildren() {
            var children = this.getEnablerChildren();
            if (children != null) {
                for (var child : children) {
                    child.parentEnablerUpdate(this);
                }
            }
        }

        private TYPE parentEnablerUpdate(Enableable parent) {
            if (this.enabler == null) {
                this.setEnabler0(parent.enabler);
            } else if (this.enabler == parent.prevEnabler) {
                this.setEnabler0(parent.enabler);
            } else if (this.enabler.inheritedEnabler != null && this.enabler.inheritedEnabler == parent.prevEnabler) {
                this.setEnabler0(this.enabler.baseEnabler.joinAnd(parent.enabler));
            } else if (this.enabler.inheritedEnabler == null && this.enabler.joinParent) {
                this.setEnabler0(this.enabler.joinAnd(parent.enabler));
            }
            return (TYPE) this;
        }

        public TYPE setEnabler(Predicate<ConfigState> enabler, String... dependencies) {
            return this.setEnabler0(new Enabler(enabler, dependencies, false));
        }

        public TYPE setEnablerInherit(Predicate<ConfigState> enabler, String... dependencies) {
            return this.setEnabler0(new Enabler(enabler, dependencies, true));
        }

        public TYPE setEnablerInherit(Predicate<ConfigState> enabler, ResourceLocation... dependencies) {
            return this.setEnabler0(new Enabler(enabler, dependencies, true));
        }

        public TYPE setEnabler(String enabler) {
            if (enabler == null) {
                return this.setEnabler(s->true);
            }
            var id = ResourceLocation.parse(enabler);
            return this.setEnabler(s->s.readBooleanOption(id), enabler);
        }

        public TYPE setEnablerAND(String... enablers) {
            var enablersId = mapIds(enablers);
            return this.setEnabler0(new Enabler(s->{
                for (var id : enablersId) {
                    if (!s.readBooleanOption(id)) {
                        return false;
                    }
                }
                return true;
            }, enablersId));
        }

        protected Enableable[] getEnablerChildren() {
            return null;
        }
    }

    public static class Page extends Enableable<Page> {
        protected Component name;
        protected Group[] groups;
        public Page(Component name, Group... groups) {
            this.name = name;
            this.groups = groups;
        }

        protected OptionPageBuilder create(ConfigBuilder builder, BuildCtx ctx) {
            var page = builder.createOptionPage();
            page.setName(this.name);
            for (var group : this.groups) {
                page.addOptionGroup(group.create(builder, ctx));
            }
            return page;
        }

        @Override
        protected Enableable[] getEnablerChildren() {
            return this.groups;
        }
    }

    public static class Group extends Enableable<Group> {
        protected Option[] options;
        public Group(Option... options) {
            this.options = options;
        }

        protected OptionGroupBuilder create(ConfigBuilder builder, BuildCtx ctx) {
            var group = builder.createOptionGroup();
            for (var option : this.options) {
                group.addOption(option.create(builder, ctx));
            }
            return group;
        }

        @Override
        protected Enableable[] getEnablerChildren() {
            return this.options;
        }
    }

    public static abstract class Option <TYPE, OPTION extends Option<TYPE,OPTION,STYPE>, STYPE extends StatefulOptionBuilder<TYPE>> extends Enableable<Option<TYPE,OPTION,STYPE>> {
        //Setter returns a post save update set
        protected String id;
        protected Component name;
        protected Component tooltip;
        protected Function<TYPE, Component> tooltipSupplier;
        protected Supplier<TYPE> getter;
        protected Consumer<TYPE> setter;
        protected OptionImpact impact;
        public Option(String id, Component name, Component tooltip, Supplier<TYPE> getter, Consumer<TYPE> setter) {
            this.id = id;
            this.name = name;
            this.tooltip = tooltip;
            this.getter = getter;
            this.setter = setter;
        }

        public Option(String id, Component name, Supplier<TYPE> getter, Consumer<TYPE> setter) {
            this.id = id;
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            if (name.getContents() instanceof TranslatableContents tc) {
                this.tooltip = Component.translatable(tc.getKey() + ".tooltip");
            } else {
                this.tooltip = name;
            }
        }

        public OPTION setTooltipSupplier(Function<TYPE, Component> supplier) {
            this.tooltipSupplier = supplier;
            return (OPTION) this;
        }

        public OPTION setImpact(OptionImpact impact) {
            this.impact = impact;
            return (OPTION) this;
        }

        protected Consumer<TYPE> postRunner;
        protected ResourceLocation[] postRunnerConflicts;
        protected ResourceLocation[] postChangeFlags;
        public OPTION setPostChangeRunner(Consumer<TYPE> postRunner, String... dontRunIfChangedVars) {
            this.postRunner = postRunner;
            this.postRunnerConflicts = mapIds(dontRunIfChangedVars);
            return (OPTION) this;
        }

        public OPTION setPostChangeFlags(String... flags) {
            this.postChangeFlags = mapIds(flags);
            return (OPTION) this;
        }

        protected abstract STYPE createType(ConfigBuilder builder);

        protected STYPE create(ConfigBuilder builder, BuildCtx ctx) {
            var option = this.createType(builder);
            option.setName(this.name);
            option.setTooltip(this.tooltip);

            Set<ResourceLocation> flags = new LinkedHashSet<>();
            if (this.postRunner != null) {
                var id = ResourceLocation.parse(this.id);
                var runner = this.postRunner;
                var getter = this.getter;
                ctx.postRunner.register(id, ()->runner.accept(getter.get()), this.postRunnerConflicts);
                flags.add(id);
            }

            if (this.postChangeFlags != null) {
                flags.addAll(List.of(this.postChangeFlags));
            }

            if (!flags.isEmpty()) {
                option.setFlags(flags.toArray(ResourceLocation[]::new));
            }

            option.setBinding(this.setter, this.getter);
            if (this.enabler != null) {
                var pred = this.enabler.tester;
                option.setEnabledProvider(s->pred.test(s), this.enabler.dependencies);
            }

            option.setStorageHandler(ctx.saveHandler);

            option.setDefaultValue(this.getter.get());

            if (this.tooltipSupplier != null) {
                option.setTooltip(this.tooltipSupplier);
            }

            if (this.impact != null) {
                option.setImpact(this.impact);
            }

            return option;
        }
    }

    public static class IntOption extends Option<Integer, IntOption, IntegerOptionBuilder> {
        protected Function<ConfigState, Range> rangeProvider;
        protected String[] rangeDependencies;
        protected ControlValueFormatter formatter = v->Component.literal(Integer.toString(v));

        public IntOption(String id, Component name, Component tooltip, Supplier<Integer> getter, Consumer<Integer> setter, Range range) {
            super(id, name, tooltip, getter, setter);
            this.rangeProvider = s->range;
        }

        public IntOption(String id, Component name, Supplier<Integer> getter, Consumer<Integer> setter, Range range) {
            super(id, name, getter, setter);
            this.rangeProvider = s->range;
        }

        public IntOption setFormatter(IntFunction<Component> formatter) {
            this.formatter = v->formatter.apply(v);
            return this;
        }

        @Override
        protected IntegerOptionBuilder createType(ConfigBuilder builder) {
            return builder.createIntegerOption(ResourceLocation.parse(this.id));
        }

        @Override
        protected IntegerOptionBuilder create(ConfigBuilder builder, BuildCtx ctx) {
            var option = super.create(builder, ctx);
            if (this.rangeDependencies == null || this.rangeDependencies.length == 0) {
                option.setRange(this.rangeProvider.apply(null));
            } else {
                option.setRangeProvider((Function<ConfigState, SteppedValidator>)(Object) this.rangeProvider, mapIds(this.rangeDependencies));
            }
            option.setValueFormatter(this.formatter);
            return option;
        }
    }

    public static class BoolOption extends Option<Boolean, BoolOption, BooleanOptionBuilder> {
        public BoolOption(String id, Component name, Component tooltip, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(id, name, tooltip, getter, setter);
        }

        public BoolOption(String id, Component name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(id, name, getter, setter);
        }

        @Override
        protected BooleanOptionBuilder createType(ConfigBuilder builder) {
            return builder.createBooleanOption(ResourceLocation.parse(this.id));
        }
    }

    public static class EnumOption<T extends Enum<T>> extends Option<T, EnumOption<T>, EnumOptionBuilder<T>> {
        private final Class<T> theEnum;
        private Function<T, Component> nameProvider = value->Component.literal(value==null?"NULL":value.toString());

        public EnumOption(String id, Class<T> theEnum, Component name, Component tooltip, Supplier<T> getter, Consumer<T> setter) {
            super(id, name, tooltip, getter, setter);
            this.theEnum = theEnum;
        }

        public EnumOption(String id, Class<T> theEnum, Component name, Supplier<T> getter, Consumer<T> setter) {
            super(id, name, getter, setter);
            this.theEnum = theEnum;
        }

        public EnumOption<T> setNameProvider(Function<T, Component> provider) {
            this.nameProvider = provider;
            return this;
        }

        @Override
        protected EnumOptionBuilder<T> createType(ConfigBuilder builder) {
            return builder.createEnumOption(ResourceLocation.parse(this.id), this.theEnum);
        }

        @Override
        protected EnumOptionBuilder<T> create(ConfigBuilder builder, BuildCtx ctx) {
            var option = super.create(builder, ctx);
            option.setElementNameProvider(this.nameProvider);
            return option;
        }
    }

    private static <F,T> T[] map(F[] from, Function<F,T> mapper, Function<Integer,T[]> factory) {
        T[] arr = factory.apply(from.length);
        for (int i = 0; i < from.length; i++) {
            arr[i] = mapper.apply(from[i]);
        }
        return arr;
    }

    private static ResourceLocation[] mapIds(String[] strings) {
        return map(strings, ResourceLocation::parse, ResourceLocation[]::new);
    }


    public static class PostApplyOps implements FlagHook {
        private record Hook(ResourceLocation name, Runnable runnable, Set<ResourceLocation> conflicts) {}
        private Map<ResourceLocation, Hook> hooks = new LinkedHashMap<>();

        public PostApplyOps register(String name, Runnable postRunner, String... conflicts) {
            return this.register(ResourceLocation.parse(name), postRunner, mapIds(conflicts));
        }

        public PostApplyOps register(ResourceLocation name, Runnable postRunner, ResourceLocation... conflicts) {
            this.hooks.put(name, new Hook(name, postRunner, new LinkedHashSet<>(List.of(conflicts))));
            return this;
        }

        protected PostApplyOps build() {
            boolean changed = false;
            do {
                changed = false;
                for (var hook : this.hooks.values()) {
                    for (var ref : new LinkedHashSet<>(hook.conflicts)) {
                        var other = this.hooks.getOrDefault(ref, null);
                        if (other != null) {
                            changed |= hook.conflicts.addAll(other.conflicts);
                        }
                    }
                }
            } while (changed);

            return this;
        }

        @Override
        public Collection<ResourceLocation> getTriggers() {
            return this.hooks.keySet();
        }

        @Override
        public void accept(Collection<ResourceLocation> identifiers, ConfigState configState) {
            for (var id : identifiers) {
                var hook = this.hooks.get(id);
                if (hook != null) {
                    if (Collections.disjoint(identifiers, hook.conflicts)) {
                        hook.runnable.run();
                    }
                }
            }
        }
    }


    private static final class BuildCtx {
        public PostApplyOps postRunner = new PostApplyOps();
        public StorageEventHandler saveHandler;
    }

    public static void buildToSodium(ConfigBuilder builder, ModOptionsBuilder options, StorageEventHandler saveHandler, Consumer<PostApplyOps> registerOps, Page... pages) {
        var ctx = new BuildCtx();
        registerOps.accept(ctx.postRunner);
        ctx.saveHandler = saveHandler;
        for (var page : pages) {
            options.addPage(page.create(builder, ctx));
        }
        options.registerFlagHook(ctx.postRunner.build());
    }
}