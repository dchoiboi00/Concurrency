//
// Simple Java implementation of the classic Dining Philosophers problem.
//
// No synchronization (yet).
//
// Graphics are *very* naive.  Philosophers are big blobs.
// Forks are little blobs.
// 
//  (c) Michael L. Scott, 2023.
//  For use by students in CSC 2/454 at the University of Rochester,
//  during the Fall 2023 term.  All other use requires written
//  permission of the author.  Originally written in 1997.
//  Updated in 2013 to use Swing.
//  Updated again in 2019 to drop support for applets.
//

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import java.util.*;
import java.lang.*;
import java.lang.Thread.*;

// This code has six main classes:
//  Dining
//      The public, "main" class.
//  Philosopher
//      Active -- extends Thread
//  Fork
//      Passive
//  Table
//      Manages the philosophers and forks and their physical layout.
//  Coordinator
//      Provides mechanisms to suspend, resume, and reset the state of
//      worker threads (philosophers).
//  UI
//      Manages graphical layout and button presses.

public class Dining {
    private static final int CANVAS_SIZE = 360;
    public static Boolean verbose = false;
    // pixels in each direction;
    // needs to agree with size in dining.html

    public static void main(String[] args) {
        // verbose
        if (args.length > 0) {
            if (args[0].equals("-v")) {
                verbose = true;
            }
        }

        JFrame f = new JFrame("Dining");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dining me = new Dining();

        final Coordinator c = new Coordinator();
        final Table t = new Table(c, CANVAS_SIZE, verbose);
        // arrange to call graphical setup from GUI thread
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    new UI(f, c, t);
                }
            });
        } catch (Exception e) {
            System.err.println("unable to create GUI");
        }

        f.pack(); // calculate size of frame
        f.setVisible(true);
    }
}

class Fork {
    private Table t;
    private static final int XSIZE = 10;
    private static final int YSIZE = 10;
    private int orig_x;
    private int orig_y;
    private int x;
    private int y;
    public int ownerId;
    public Boolean dirty;

    // Constructor.
    // cx and cy indicate coordinates of center.
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    
    public Fork(Table T, int cx, int cy, int o_id, Boolean d) {
        t = T;
        orig_x = cx;
        orig_y = cy;
        x = cx;
        y = cy;
        ownerId = o_id;
        dirty = d;
    }

    public void reset() {
        clear();
        ownerId = -1;
        dirty = true;
        x = orig_x;
        y = orig_y;
        t.repaint();
    }

    // who wants it
    // arguments are coordinates of acquiring philosopher's center
    // picks up the fork, move coordinates of fork. Indicating P has picked a
    // specific fork up
    public void acquire(int px, int py) {
        // clear paint
        clear();
        // gets new location of the philosopher, px and py
        // redraw fork
        x = (orig_x + px) / 2;
        y = (orig_y + py) / 2;
        t.repaint();
    }

    // done using
    public void release() {
        reset();
    }

    // render self
    //
    public void draw(Graphics g) {
        if (dirty) g.setColor(new Color(102,51,0));
        else g.setColor(Color.LIGHT_GRAY);
        
        g.fillOval(x - XSIZE / 2, y - YSIZE / 2, XSIZE, YSIZE);
    }

    // erase self
    //
    private void clear() {
        Graphics g = t.getGraphics();
        g.setColor(t.getBackground());
        g.fillOval(x - XSIZE / 2, y - YSIZE / 2, XSIZE, YSIZE);
    }
}

// .start() starts a new thread
// Thread goes from new state to runnable state
// When this thread gets a chance, run() method runs
class Philosopher extends Thread {
    // Thinking
    private static final Color THINK_COLOR = Color.blue;
    // Waiting
    private static final Color WAIT_COLOR = Color.red;
    // Eating
    private static final Color EAT_COLOR = Color.green;

    // Time between blue to red
    private static final double THINK_TIME = 4.0;
    // Time between red to green
    // Time between becoming hungry and grabbing first fork
    private static final double FUMBLE_TIME = 2.0;
    // Time between green and blue again (drops forks when done eating)
    private static final double EAT_TIME = 3.0;

    private Coordinator c;
    private Table t;
    private static final int XSIZE = 50;
    private static final int YSIZE = 50;
    private int x;
    private int y;
    private Fork left_fork;
    private Fork right_fork;
    private Random prn;
    private Color color;
    // philosopher owner id
    private int philosopher_number_indicator;
    // Boolean value to indicate last philosopher on the table or not
    private Boolean last_person;
    // printing variable passed in when creating a Philosopher instance within the Table's constructor
    private Boolean print_v;

    // Constructor.
    // cx and cy indicate coordinates of center
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    //
    public Philosopher(Table T, int cx, int cy,
            Fork lf, Fork rf, Coordinator C, int i, Boolean l, Boolean p) {
        t = T;
        x = cx;
        y = cy;
        left_fork = lf;
        right_fork = rf;
        c = C;
        prn = new Random();
        color = THINK_COLOR;
        philosopher_number_indicator = i;
        last_person = l;
        print_v = p;
    }

    // start method of Thread calls run()
    // When thread gets a chance, this method runs
    public void run() {
        // while true
        for (;;) {
            try {
                // Think doesn't require forks or any shared data
                if (c.gate())
                    delay(EAT_TIME / 2.0);
                think();

                // We label this as a critical section to prevent situation (a bad race) where
                // when different philosophers might access with the same fork at the sametime
                // and does unexpected operations to the forks
                synchronized (left_fork) {
                    synchronized (right_fork) {
                        if (c.gate())
                            delay(THINK_TIME / 2.0);
                        hunger();
                        if (c.gate())
                            delay(FUMBLE_TIME / 2.0);
                        eat();
                    }
                }

            } catch (ResetException e) {
                color = THINK_COLOR;
                t.repaint();
            }
        }
    }

    // render self
    //
    public void draw(Graphics g) {
        g.setColor(color);
        g.fillOval(x - XSIZE / 2, y - YSIZE / 2, XSIZE, YSIZE);
        String label = String.valueOf(this.philosopher_number_indicator);
        int labelX = x - g.getFontMetrics().stringWidth(label) / 2;
        int labelY = y + g.getFontMetrics().getAscent() / 2;
        g.setColor(Color.WHITE);

        g.drawString(label, labelX, labelY);

    }

    // sleep for secs +- FUDGE (%) seconds
    private static final double FUDGE = 0.2;

    private void delay(double secs) throws ResetException {
        double ms = 1000 * secs;
        int window = (int) (2.0 * ms * FUDGE);
        int add_in = prn.nextInt() % window;
        int original_duration = (int) ((1.0 - FUDGE) * ms + add_in);
        int duration = original_duration;
        for (;;) {
            try {
                Thread.sleep(duration);
                return;
            } catch (InterruptedException e) {
                if (c.isReset()) {
                    throw new ResetException();
                } else { // suspended
                    c.gate(); // wait until resumed
                    duration = original_duration / 2;
                    // don't wake up instantly; sleep for about half
                    // as long as originally instructed
                }
            }
        }
    }

    // P is just thinking, blue
    private void think() throws ResetException {
        // used to check if "-v" is passed in the command line when running the program
        if (print_v) {
            System.out.println("Philosopher " + philosopher_number_indicator + " thinking");
        }
        color = THINK_COLOR;
        t.repaint();
        delay(THINK_TIME);
    }

    // P is hungry, red
    // while in hunger status, it requests for forks
    // acquire, it picks up the fork
    private void hunger() throws ResetException {
        color = WAIT_COLOR;
        t.repaint();
        delay(FUMBLE_TIME);

        // left fork is the one given initially to the person on the left. So we request
        // that first?
        // x and y is a way to indicate specific philosopher
        // ask for left fork from other philosopher, but yields until it's available

        // This boolean variable is used to check if this Philosopher is the last person
        // or not
        // If not, we will always acquire left fork first and then right fork
        // If so, we will always acquire the right fork and then the left fork
        // this way, we can prevent a deadlock situation (which is likely to happen
        // intially when all forks are dirty) Where each Philosopher acquire the left fork 
        // together at the sametime
        if (!last_person) { // left fork first, then right fork
            while (left_fork.ownerId != philosopher_number_indicator) {
                if (left_fork.dirty) {
                    // change ownership to current philosopher's id
                    left_fork.ownerId = philosopher_number_indicator;
                    left_fork.dirty = false;
                    left_fork.acquire(x, y);
                } else {
                    Thread.yield(); // yield the core while waiting
                }

            }
            // gives other threads the same priority a chance to execute
            Thread.yield(); // you aren't allowed to remove this

            while (right_fork.ownerId != philosopher_number_indicator) {
                if (right_fork.dirty) {
                    // change ownership to current philosopher's id
                    right_fork.ownerId = philosopher_number_indicator;
                    // sets it to clean
                    right_fork.dirty = false;
                    right_fork.acquire(x, y);

                } else {
                    Thread.yield(); // yield the core while waiting
                }
            }
        } else { // last philosopher: right fork first, then left fork
            while (right_fork.ownerId != philosopher_number_indicator) {
                if (right_fork.dirty) {
                    // change ownership to current philosopher's id
                    right_fork.ownerId = philosopher_number_indicator;
                    // sets it to clean
                    right_fork.dirty = false;
                    right_fork.acquire(x, y);
                } else {
                    Thread.yield(); // yield the core while waiting

                }

            }
            Thread.yield();
            while (left_fork.ownerId != philosopher_number_indicator) {
                if (left_fork.dirty) {
                    // change ownership to current philosopher's id
                    left_fork.ownerId = philosopher_number_indicator;

                    left_fork.dirty = false;
                    left_fork.acquire(x, y);
                } else {
                    Thread.yield(); // yield the core while waiting
                }

            }
        }
    }

    // P is eating right now, green
    // eats then release fork (puts fork down)
    // when forks
    private void eat() throws ResetException {
        // used to check if "-v" is passed in the command line when running the program
        if (print_v) {

            System.out.println("Philosopher " + philosopher_number_indicator + " eating");
        }

        color = EAT_COLOR;
        t.repaint();
        // Eating
        delay(EAT_TIME);
        // done eating
        left_fork.dirty = true; // releases left fork then yields
        left_fork.release();
        Thread.yield(); // you aren't allowed to remove this
        right_fork.dirty = true;
        right_fork.release();
    }
} // end of Philosopher class

// Graphics panel in which philosophers and forks appear.
//
class Table extends JPanel {
    private static final int NUM_PHILS = 5;

    // following fields are set by construcctor:
    private final Coordinator c;
    private Fork[] forks;
    private Philosopher[] philosophers;

    public void pause() {
        c.pause();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
    }

    // Called by the UI when it wants to start over.
    //
    public void reset() {
        c.reset();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
        for (int i = 0; i < NUM_PHILS; i++) {
            forks[i].reset();
        }
    }

    // The following method is called automatically by the graphics
    // system when it thinks the Table canvas needs to be re-displayed.
    // This can happen because code elsewhere in this program called
    // repaint(), or because of hiding/revealing or open/close
    // operations in the surrounding window system.
    //
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int i = 0; i < NUM_PHILS; i++) {
            forks[i].draw(g);
            philosophers[i].draw(g);
        }
        g.setColor(Color.black);
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
    }

    // Constructor
    //
    // Note that angles are measured in radians, not degrees.
    // The origin is the upper left corner of the frame.
    // When new Table instance is created, we invoke constructor of Table,
    // which creates instances of Philosophers and Forks
    // Table is created within Main function and passed into UI instance
    // Table also starts the Philosopher (Threads) instances
    public Table(Coordinator C, int CANVAS_SIZE, Boolean print_v) { // constructor
        c = C;
        forks = new Fork[NUM_PHILS];
        philosophers = new Philosopher[NUM_PHILS];
        setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));

        // Where forks and philosophers are layed out at on the table
        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI / 2 + 2 * Math.PI / NUM_PHILS * (i - 0.5);
            // new Fork(Table t, int x, int y)
            // Same Table instance for alll forks and all philosophers
            forks[i] = new Fork(this,
                    (int) (CANVAS_SIZE / 2.0 + CANVAS_SIZE / 6.0 * Math.cos(angle)),
                    (int) (CANVAS_SIZE / 2.0 - CANVAS_SIZE / 6.0 * Math.sin(angle)), -1, true);
        }

        // As we create a thread, we start it as well
        // we pass in two fork instances when creating a philosopher instance
        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI / 2 + 2 * Math.PI / NUM_PHILS * i;
            // philosopher constructor (Table, cx, cy, lf, rf, coordinator)

            if (i == NUM_PHILS - 1) {
                philosophers[i] = new Philosopher(this,
                        (int) (CANVAS_SIZE / 2.0 + CANVAS_SIZE / 3.0 * Math.cos(angle)),
                        (int) (CANVAS_SIZE / 2.0 - CANVAS_SIZE / 3.0 * Math.sin(angle)),
                        forks[i],
                        forks[(i + 1) % NUM_PHILS],
                        c, i, true, print_v);
            } else {

                philosophers[i] = new Philosopher(this,
                        (int) (CANVAS_SIZE / 2.0 + CANVAS_SIZE / 3.0 * Math.cos(angle)),
                        (int) (CANVAS_SIZE / 2.0 - CANVAS_SIZE / 3.0 * Math.sin(angle)),
                        forks[i],
                        forks[(i + 1) % NUM_PHILS],
                        c, i, false, print_v);
            }
            // starts a new thread
            // Thread goes from new state to runnable state
            // When this thread gets a chance, run() method runs
            philosophers[i].start();
        }
    }
}

class ResetException extends Exception {
};

// The Coordinator serves to slow down execution, so that behavior is
// visible on the screen, and to notify all running threads when the user
// wants them to reset.
// Each thread also has a coordinator
// Used to indicate the state of the thread
class Coordinator {

    public enum State {
        PAUSED, RUNNING, RESET
    }

    private State state = State.PAUSED;

    public synchronized boolean isPaused() {
        return (state == State.PAUSED);
    }

    public synchronized void pause() {
        state = State.PAUSED;
    }

    public synchronized boolean isReset() {
        return (state == State.RESET);
    }

    public synchronized void reset() {
        state = State.RESET;
    }

    public synchronized void resume() {
        state = State.RUNNING;
        notifyAll(); // wake up all waiting threads
    }

    // Return true if we were forced to wait because the coordinator was
    // paused or reset.
    // Can be called to check the state of the thread anytime during thread.run(),
    // an infinite loop
    public synchronized boolean gate() throws ResetException {
        if (state == State.PAUSED || state == State.RESET) {
            try {
                wait();
            } catch (InterruptedException e) {
                if (isReset()) {
                    throw new ResetException();
                }
            }
            return true; // waited
        }
        return false; // didn't wait
    }
}

// Class UI is the user interface. It displays a Table canvas above
// a row of buttons. Actions (event handlers) are defined for each of
// the buttons. Depending on the state of the UI, either the "run" or
// the "pause" button is the default (highlighted in most window
// systems); it will often self-push if you hit carriage return.
//
class UI extends JPanel {
    private final Coordinator c;
    private final Table t;

    private final JRootPane root;
    private static final int externalBorder = 6;

    private static final int stopped = 0;
    private static final int running = 1;
    private static final int paused = 2;

    private int state = stopped;

    // Constructor
    //
    public UI(RootPaneContainer pane, Coordinator C, Table T) {
        final UI u = this;
        c = C;
        t = T;

        final JPanel b = new JPanel(); // button panel

        final JButton runButton = new JButton("Run");
        final JButton pauseButton = new JButton("Pause");
        final JButton resetButton = new JButton("Reset");
        final JButton quitButton = new JButton("Quit");

        runButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                c.resume();
                root.setDefaultButton(pauseButton);
            }
        });
        pauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.pause();
                root.setDefaultButton(runButton);
            }
        });
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.reset();
                root.setDefaultButton(runButton);
            }
        });
        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        // put the buttons into the button panel:
        b.setLayout(new FlowLayout());
        b.add(runButton);
        b.add(pauseButton);
        b.add(resetButton);
        b.add(quitButton);

        // put the Table canvas and the button panel into the UI:
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(
                externalBorder, externalBorder, externalBorder, externalBorder));
        add(t);
        add(b);

        // put the UI into the Frame
        pane.getContentPane().add(this);
        root = getRootPane();
        root.setDefaultButton(runButton);
    }
}
