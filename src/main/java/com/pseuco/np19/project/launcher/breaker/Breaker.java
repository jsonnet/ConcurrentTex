package com.pseuco.np19.project.launcher.breaker;

import com.pseuco.np19.project.launcher.breaker.item.*;

import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.pow;

/**
 * Implements a multi-tolerance variant of the Knuth-Plass line-breaking algorithm.
 *
 * For more details read the project description or the original paper.
 *
 * @param <T> The inner type of the items.
 */
public class Breaker<T> {
    /**
     * Takes a sequence of items and breaks it into pieces.
     *
     * @param parameters The parameters for the algorithm.
     * @param items The sequence of items to break into pieces.
     * @param tolerances A list of tolerances to use.
     * @param pieceSize The size of each piece.
     * @param <T> The inner type of the items.
     * @return A list of pieces representing a partition of the input sequence.
     * @throws UnableToBreakException Thrown when the sequence cannot be broken with the given parameters.
     */
    public static <T> List<Piece<T>> breakIntoPieces(Parameters parameters, List<Item<T>> items, List<Double> tolerances, double pieceSize) throws UnableToBreakException {
        final Breaker<T> breaker = parameters.createBreaker(pieceSize, tolerances);
        breaker.pushAll(items);

        if (!breaker.hasPieces()) {
            throw new UnableToBreakException();
        }

        return breaker.getPieces();
    }

    /**
     * Implementation of an {@link ItemVisitor} used to process a sequence of items.
     *
     * @param <T> The inner type of the items.
     */
    private static final class Visitor<T> implements ItemVisitor<T> {
        private final Breaker<T> breaker;

        private Visitor(Breaker<T> breaker) {
            this.breaker = breaker;
        }

        @Override
        public void visit(Box<T> box) {
            this.breaker.lastBreakpoints.clear();
            this.breaker.previousWasBox = true;
            this.breaker.totalAdd(box);
            this.breaker.position++;
        }

        @Override
        public void visit(Glue<T> glue) {
            for (Breakpoint breakpoint : this.breaker.lastBreakpoints) {
                // eat up the glue after each breakpoint
                breakpoint.setTotal(breakpoint.getTotal().add(glue));
            }
            if (this.breaker.previousWasBox) {
                this.breaker.considerBreakpointAt(glue);
            }
            this.breaker.previousWasBox = false;
            this.breaker.totalAdd(glue);
            this.breaker.position++;
        }

        @Override
        public void visit(Penalty<T> penalty) {
            this.breaker.lastBreakpoints.clear();
            if (penalty.getPenalty() != this.breaker.parameters.getInfinity()) {
                this.breaker.considerBreakpointAt(penalty);
            }
            this.breaker.previousWasBox = false;
            this.breaker.position++;
        }
    }

    private final double pieceSize;

    private final List<Double> tolerances;

    private final Parameters parameters;

    private final Visitor<T> visitor;

    private final List<Breakpoint> activeBreakpoints = new LinkedList<>();
    private final List<Breakpoint> lastBreakpoints = new LinkedList<>();

    private final List<Item<T>> items = new ArrayList<>();

    private double tolerance;

    private boolean failed = false;

    private int position = 0;

    private boolean previousWasBox;

    private Sum total;

    private Breakpoint segmentInitial;
    private Iterator<Double> segmentTolerances;
    private int segmentStart;

    Breaker(double pieceSize, List<Double> tolerances, Parameters parameters) {
        this.pieceSize = pieceSize;
        this.tolerances = tolerances;
        this.parameters = parameters;

        this.visitor = new Visitor<>(this);

        this.startSegment();
    }

    /**
     * @return Returns `true` if and only if the sequence cannot be broken with the given parameters.
     */
    public boolean hasFailed() {
        return this.failed;
    }

    /**
     * @return Returns `true` if and only if `getPieces` will return a list of pieces (a non-null value).
     */
    public boolean hasPieces() {
        return !this.activeBreakpoints.isEmpty();
    }

    /**
     * @return Returns the list of pieces in which the sequence has been broken.
     */
    public List<Piece<T>> getPieces() {
        Optional<Breakpoint> best = this.activeBreakpoints.stream().min(Breakpoint.DEMERITS_COMPARATOR);
        return best.map(breakpoint -> breakpoint.getPieces(this.items)).orElse(null);
    }

    /**
     * Pushes an item into the breaker, i.e., extends the item-sequence to break with the given item.
     *
     * Can be used by lazy implementations to already start breaking while not all items are available.
     *
     * @param item The item to extend the item-sequence with.
     */
    public void push(Item<T> item) {
        if (!this.failed) {
            this.items.add(item);
            item.accept(this.visitor);

            if (!this.hasPieces() && !this.failed) {
                this.nextTolerance();
            }

            if (item.getPenalty() == -this.parameters.getInfinity()) {
                this.startSegment();
            }
        } else {
            throw new IllegalStateException("Breaking failed! Unable to push item!");
        }
    }

    /**
     * Pushes a list of items into the breaker.
     *
     * Can be used by lazy implementations to already start breaking while not all items are available.
     *
     * @param items The items to extend the item-sequence with.
     */
    public void pushAll(Iterable<Item<T>> items) {
        for (Item<T> item : items) {
            this.push(item);
            if (this.failed) {
                return;
            }
        }
    }

    /**
     * Starts a new segment and resets the algorithm.
     */
    private void startSegment() {
        this.segmentStart = this.position;
        this.segmentTolerances = this.tolerances.iterator();

        if (this.position == 0) {
            this.segmentInitial = new Breakpoint(this.segmentStart, Fitness.NORMAL);
            this.nextTolerance();
        } else {
            final Optional<Breakpoint> best = this.activeBreakpoints.stream().min(Breakpoint.DEMERITS_COMPARATOR);
            if (best.isPresent()) {
                final Breakpoint breakpoint = best.get();
                breakpoint.setDemerits(0);
                breakpoint.setFlagged(false);
                this.segmentInitial = breakpoint;
                this.nextTolerance();
            } else {
                this.segmentInitial = null;
            }
        }
    }

    /**
     * Advances the tolerance to use for the current segment.
     */
    private void nextTolerance() {
        if (this.segmentTolerances.hasNext()) {
            this.tolerance = this.segmentTolerances.next();
            this.restart();
        } else {
            this.activeBreakpoints.clear();
            this.failed = true;
        }
    }

    /**
     * Restarts the Knuth-Plass algorithm and advances the tolerance.
     *
     * If the sequence cannot be broken with the current tolerance, then the next bigger
     * tolerance is used and the algorithm is run again on all items.
     */
    private void restart() {
        this.activeBreakpoints.clear();
        this.lastBreakpoints.clear();

        if (this.segmentInitial != null) {
            this.position = this.segmentStart;
            this.previousWasBox = false;
            this.total = new Sum();

            this.segmentInitial.setTotal(this.total);

            this.activeBreakpoints.add(this.segmentInitial);

            for (int index = this.position; index < this.items.size(); index++) {
                final Item<T> item = this.items.get(index);
                item.accept(this.visitor);
                if (!this.hasPieces()) {
                    if (!this.failed) {
                        this.nextTolerance();
                    }
                    return;
                }
            }
        }
    }

    /**
     * Computes the demerits for breaking at the given item with the given ratio.
     *
     * @param item The item to break at.
     * @param ratio The ratio of the resulting piece.
     * @return The computed demerits.
     */
    private double computeDemerits(Item<T> item, double ratio) {
        final double badness = pow(100 * abs(ratio), 3);
        final double demerits = pow(this.parameters.getDemeritsBreak() + badness, 2);
        if (item.getPenalty() >= 0) {
            return demerits + pow(item.getPenalty(), 2);
        } else if (item.getPenalty() != -this.parameters.getInfinity()) {
            return demerits - pow(item.getPenalty(), 2);
        } else {
            return demerits;
        }
    }

    private void totalAdd(Item<T> item) {
        this.total = this.total.add(item);
    }

    private double computeRatio(Sum pieceSize) {
        double diff = this.pieceSize - pieceSize.getSize();
        if (diff > 0) {
            if (pieceSize.getStretch() > 0) {
                return diff / pieceSize.getStretch();
            } else {
                return this.parameters.getInfinity();
            }
        } else if (diff < 0) {
            if (pieceSize.getShrink() > 0) {
                return diff / pieceSize.getShrink();
            } else {
                return this.parameters.getInfinity();
            }
        } else {
            return 0;
        }
    }

    /**
     * Here is where the magic happens. Considers a breakpoint at the given item.
     *
     * For further details, we refer to the original paper and the project description.
     *
     * @param item The item to consider a breakpoint at.
     */
    private void considerBreakpointAt(Item<T> item) {
        final EnumMap<Fitness, Breakpoint> best = new EnumMap<>(Fitness.class);

        // we use this parameter to intentionally slow down the algorithm (this is for testing purposes)
        final int rounds = this.parameters.getRounds();

        for (int round = rounds; round > 0; round--) {
            best.clear();

            final ListIterator<Breakpoint> iterator;
            if (round > 1) {
                iterator = (new ArrayList<>(this.activeBreakpoints)).listIterator();
            } else {
                iterator = this.activeBreakpoints.listIterator();
            }

            while (iterator.hasNext()) {
                final Breakpoint current = iterator.next();

                Sum pieceSize = this.total.sub(current.getTotal());

                if (item.getPenalty() != 0) {
                    pieceSize = pieceSize.add(item);
                }

                final double ratio = this.computeRatio(pieceSize);

                if (ratio < -1 || item.getPenalty() == -this.parameters.getInfinity()) {
                    iterator.remove();
                }
                if (-1 <= ratio && ratio <= this.tolerance) {
                    double demerits = this.computeDemerits(item, ratio);

                    final Fitness fitness = Fitness.byRatio(ratio);

                    if (current.getFitness() != fitness) {
                        demerits += this.parameters.getDemeritsFitness();
                    }
                    if (current.getFlagged() == item.getFlagged()) {
                        demerits += this.parameters.getDemeritsFlagged();
                    }

                    demerits += current.getDemerits();

                    Breakpoint breakpoint = best.get(fitness);
                    if (breakpoint == null) {
                        breakpoint = new Breakpoint(this.position + 1, fitness);
                        best.put(fitness, breakpoint);
                    } else if (breakpoint.getDemerits() < demerits) {
                        continue;
                    }

                    breakpoint.setRatio(ratio);
                    breakpoint.setDemerits(demerits);
                    breakpoint.setParent(current);
                    breakpoint.setFlagged(item.getFlagged());
                    if (item.getPenalty() != 0) {
                        breakpoint.setTotal(this.total);
                    } else {
                        breakpoint.setTotal(this.total.add(item));
                    }
                }
            }
        }

        this.activeBreakpoints.addAll(best.values());
        this.lastBreakpoints.addAll(best.values());
    }
}
