import tkinter as tk
from tkinter import ttk
import json
import os
import time
from datetime import datetime, date
from plyer import notification
import matplotlib.pyplot as plt

WORK_TIME = 25 * 60
BREAK_TIME = 5 * 60
LONG_BREAK = 15 * 60

DATA_FILE = "pomodoro_data.json"
TASK_FILE = "pomodoro_tasks.json"


class PomodoroApp:

    def __init__(self, root):

        self.root = root
        self.root.title("Pomodoro")
        self.root.geometry("520x620")

        self.running = False
        self.mode = "work"
        self.time_left = WORK_TIME
        self.last_update = time.time()

        self.tasks = []

        self.load_data()
        self.load_tasks()

        self.build_ui()
        self.update_ui()

        root.protocol("WM_DELETE_WINDOW", self.on_close)

    # -----------------------
    # UTIL
    # -----------------------

    def today(self):
        return date.today().isoformat()

    def format_time(self, seconds):

        h = seconds // 3600
        m = (seconds % 3600) // 60
        s = seconds % 60

        return f"{h:02}:{m:02}:{s:02}"

    def mode_text(self):

        if self.mode == "work":
            return "WORK"

        if self.mode == "break":
            return "BREAK"

        if self.mode == "long_break":
            return "LONG BREAK"

    # -----------------------
    # DATA
    # -----------------------

    def load_data(self):

        if not os.path.exists(DATA_FILE):

            self.data = {"stats": {}}

            with open(DATA_FILE, "w", encoding="utf-8") as f:
                json.dump(self.data, f, indent=2)

        else:

            with open(DATA_FILE, "r", encoding="utf-8") as f:
                self.data = json.load(f)

        today = self.today()

        if today not in self.data["stats"]:

            self.data["stats"][today] = {
                "work_time": 0,
                "break_time": 0,
                "cycles": 0,
                "breaks": 0,
                "tasks_done": 0
            }

    def save_data(self):

        with open(DATA_FILE, "w", encoding="utf-8") as f:
            json.dump(self.data, f, indent=2, ensure_ascii=False)

    def today_stats(self):

        return self.data["stats"][self.today()]

    # -----------------------
    # TASKS
    # -----------------------

    def load_tasks(self):

        if not os.path.exists(TASK_FILE):

            with open(TASK_FILE, "w") as f:
                json.dump([], f)

        with open(TASK_FILE, "r", encoding="utf-8") as f:
            self.tasks = json.load(f)

    def save_tasks(self):

        with open(TASK_FILE, "w", encoding="utf-8") as f:
            json.dump(self.tasks, f, indent=2, ensure_ascii=False)

    def refresh_tasks(self):

        self.task_list.delete(0, tk.END)

        for t in self.tasks:

            text = t["text"]

            if t["done"]:
                text = "✔ " + text

            self.task_list.insert(tk.END, text)

    def add_task(self):

        text = self.task_entry.get()

        if not text:
            return

        task = {
            "text": text,
            "done": False,
            "created": datetime.now().isoformat()
        }

        self.tasks.append(task)

        self.task_entry.delete(0, tk.END)

        self.refresh_tasks()
        self.save_tasks()

    def toggle_task(self):

        sel = self.task_list.curselection()

        if not sel:
            return

        i = sel[0]

        self.tasks[i]["done"] = not self.tasks[i]["done"]

        if self.tasks[i]["done"]:
            self.today_stats()["tasks_done"] += 1

        self.refresh_tasks()

        self.save_tasks()
        self.save_data()

    def delete_task(self):

        sel = self.task_list.curselection()

        if not sel:
            return

        i = sel[0]

        del self.tasks[i]

        self.refresh_tasks()
        self.save_tasks()

    # -----------------------
    # UI
    # -----------------------

    def build_ui(self):

        main = ttk.Frame(self.root, padding=15)
        main.pack(fill="both", expand=True)

        self.timer_label = ttk.Label(
            main,
            text="25:00",
            font=("Segoe UI", 48)
        )
        self.timer_label.pack()

        self.mode_label = ttk.Label(main, text="WORK", font=("Segoe UI", 14))
        self.mode_label.pack()

        btn = ttk.Frame(main)
        btn.pack(pady=10)

        ttk.Button(btn, text="Start", command=self.start).grid(row=0, column=0)
        ttk.Button(btn, text="Pause", command=self.pause).grid(row=0, column=1)
        ttk.Button(btn, text="Reset", command=self.reset).grid(row=0, column=2)

        switch = ttk.Frame(main)
        switch.pack()

        ttk.Button(switch, text="Work", command=self.set_work).grid(row=0, column=0)
        ttk.Button(switch, text="Break", command=self.set_break).grid(row=0, column=1)
        ttk.Button(switch, text="Long Break", command=self.set_long_break).grid(row=0, column=2)

        stats = ttk.LabelFrame(main, text="Today stats")
        stats.pack(fill="x", pady=10)

        self.work_label = ttk.Label(stats)
        self.work_label.pack()

        self.break_label = ttk.Label(stats)
        self.break_label.pack()

        self.cycle_label = ttk.Label(stats)
        self.cycle_label.pack()

        self.break_count_label = ttk.Label(stats)
        self.break_count_label.pack()

        self.tasks_done_label = ttk.Label(stats)
        self.tasks_done_label.pack()

        ttk.Button(stats, text="Productivity graph", command=self.show_graph).pack(pady=5)

        task_frame = ttk.LabelFrame(main, text="Tasks")
        task_frame.pack(fill="both", expand=True)

        entry_frame = ttk.Frame(task_frame)
        entry_frame.pack(fill="x")

        self.task_entry = ttk.Entry(entry_frame)
        self.task_entry.pack(side="left", fill="x", expand=True)

        ttk.Button(entry_frame, text="Add", command=self.add_task).pack(side="left")

        list_frame = ttk.Frame(task_frame)
        list_frame.pack(fill="both", expand=True)

        scrollbar = ttk.Scrollbar(list_frame)

        self.task_list = tk.Listbox(
            list_frame,
            yscrollcommand=scrollbar.set
        )

        scrollbar.config(command=self.task_list.yview)

        scrollbar.pack(side="right", fill="y")
        self.task_list.pack(fill="both", expand=True)

        act = ttk.Frame(task_frame)
        act.pack()

        ttk.Button(act, text="Toggle Done", command=self.toggle_task).grid(row=0, column=0)
        ttk.Button(act, text="Delete", command=self.delete_task).grid(row=0, column=1)

        self.refresh_tasks()

    # -----------------------
    # TIMER
    # -----------------------

    def start(self):

        if not self.running:

            self.running = True
            self.last_update = time.time()
            self.tick()

    def pause(self):
        self.running = False

    def reset(self):

        self.running = False

        if self.mode == "work":
            self.time_left = WORK_TIME
        elif self.mode == "break":
            self.time_left = BREAK_TIME
        else:
            self.time_left = LONG_BREAK

        self.update_ui()

    def tick(self):

        if not self.running:
            return

        now = time.time()
        delta = int(now - self.last_update)

        if delta > 0:

            self.last_update = now

            for _ in range(delta):

                self.time_left -= 1

                stats = self.today_stats()

                if self.mode == "work":
                    stats["work_time"] += 1
                else:
                    stats["break_time"] += 1

                if self.time_left <= 0:
                    self.switch_cycle()

        self.update_ui()

        self.root.after(200, self.tick)

    def switch_cycle(self):

        stats = self.today_stats()

        if self.mode == "work":

            stats["cycles"] += 1
            stats["breaks"] += 1

            if stats["cycles"] % 4 == 0:

                self.mode = "long_break"
                self.time_left = LONG_BREAK
                self.notify("Long break!")

            else:

                self.mode = "break"
                self.time_left = BREAK_TIME
                self.notify("Break!")

        else:

            self.mode = "work"
            self.time_left = WORK_TIME
            self.notify("Back to work!")

        self.save_data()

    def set_work(self):

        self.mode = "work"
        self.time_left = WORK_TIME
        self.update_ui()

    def set_break(self):

        self.mode = "break"
        self.time_left = BREAK_TIME
        self.update_ui()

    def set_long_break(self):

        self.mode = "long_break"
        self.time_left = LONG_BREAK
        self.update_ui()

    # -----------------------
    # UI UPDATE
    # -----------------------

    def update_ui(self):

        mins = self.time_left // 60
        secs = self.time_left % 60

        self.timer_label.config(text=f"{mins:02}:{secs:02}")

        self.mode_label.config(text=self.mode_text())

        stats = self.today_stats()

        self.work_label.config(
            text="Work time: " + self.format_time(stats["work_time"])
        )

        self.break_label.config(
            text="Break time: " + self.format_time(stats["break_time"])
        )

        self.cycle_label.config(
            text=f"Cycles: {stats['cycles']}"
        )

        self.break_count_label.config(
            text=f"Breaks: {stats['breaks']}"
        )

        self.tasks_done_label.config(
            text=f"Tasks done: {stats['tasks_done']}"
        )

    # -----------------------
    # GRAPH
    # -----------------------

    def show_graph(self):

        days = []
        work = []
        tasks = []

        for d, v in self.data["stats"].items():

            days.append(d)
            work.append(v["work_time"] / 3600)
            tasks.append(v["tasks_done"])

        plt.plot(days, work, label="Work hours")
        plt.plot(days, tasks, label="Tasks done")

        plt.legend()
        plt.xticks(rotation=45)
        plt.title("Productivity by day")
        plt.grid(True)

        plt.show()

    # -----------------------
    # NOTIFY
    # -----------------------

    def notify(self, text):

        notification.notify(
            title="Pomodoro",
            message=text,
            timeout=5
        )

    # -----------------------
    # CLOSE
    # -----------------------

    def on_close(self):

        self.save_data()
        self.save_tasks()

        self.root.destroy()


root = tk.Tk()
app = PomodoroApp(root)
root.mainloop()