package org.powernukkitx.terra.crosschunk.bounds;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** Thread-confined root placement context shared by Terra BufferedWorld descendants. */
public final class PlacementGuardContext {
    private static final ThreadLocal<Deque<PlacementRuntimeGuard>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private PlacementGuardContext() {}

    public static Scope installRoot(PlacementRuntimeGuard guard) {
        if (guard == null) throw new NullPointerException("guard");
        Deque<PlacementRuntimeGuard> stack = STACK.get();
        if (!stack.isEmpty()) throw new IllegalStateException("A root placement guard is already installed");
        stack.push(guard);
        return new Scope(guard, Thread.currentThread());
    }

    public static Optional<PlacementRuntimeGuard> current() {
        Deque<PlacementRuntimeGuard> stack = STACK.get();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    public static final class Scope implements AutoCloseable {
        private final PlacementRuntimeGuard expected;
        private final Thread owner;
        private boolean closed;

        private Scope(PlacementRuntimeGuard expected, Thread owner) {
            this.expected = expected;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != owner) throw new IllegalStateException("Guard scope is thread-confined");
            Deque<PlacementRuntimeGuard> stack = STACK.get();
            if (stack.isEmpty() || stack.peek() != expected)
                throw new IllegalStateException("Placement guard scopes must close LIFO");
            stack.pop();
            if (stack.isEmpty()) STACK.remove();
            closed = true;
        }
    }
}
