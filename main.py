import json
import os
import time
import uuid
from datetime import datetime, date
import tkinter as tk
from tkinter import ttk, messagebox

from plyer import notification
import matplotlib.pyplot as plt


WORK_TIME = 25 * 60
BREAK_TIME = 5 * 60
LONG_BREAK_TIME = 15 * 60

DATA_FILE = "pomodoro_data.json"
TASK_FILE = "pomodoro_tasks.json"


class PomodoroApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Pomodoro Tracker")
        self.root.geometry("780x900")
        self.root.minsize(720, 720)

        self.running = False
        self.mode = "work"
        self.control_mode = "automatic"

        self.time_left = WORK_TIME
        self.period_duration = WORK_TIME
        self.manual_elapsed = 0

        self.last_update = time.time()
        self.tasks = []

        self.stats_window = None
        self.stats_labels = {}

        self.load_data()
        self.load_tasks()

        self.build_ui()
        self.update_ui()

        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    # --------------------------
    # HELPERS
    # --------------------------

    def today(self):
        return date.today().isoformat()

    def now_iso(self):
        return datetime.now().isoformat()

    def format_hms(self, seconds):
        hours = seconds // 3600
        minutes = (seconds % 3600) // 60
        secs = seconds % 60
        return f"{hours:02}:{minutes:02}:{secs:02}"

    def format_ms(self, seconds):
        minutes = seconds // 60
        secs = seconds % 60
        return f"{minutes:02}:{secs:02}"

    def mode_text(self):
        if self.mode == "work":
            return "РАБОТА"
        if self.mode == "break":
            return "ПЕРЕРЫВ"
        return "ДЛИННЫЙ ПЕРЕРЫВ"

    def control_mode_text(self):
        return "АВТОМАТИЧЕСКИЙ" if self.control_mode == "automatic" else "РУЧНОЙ"

    def get_mode_color(self):
        if self.mode == "work":
            return "#15803d"
        if self.mode == "break":
            return "#d97706"
        return "#2563eb"

    def safe_int(self, value, default):
        try:
            return int(value)
        except Exception:
            return default

    def default_duration_for_mode(self, mode):
        if mode == "work":
            return WORK_TIME
        if mode == "break":
            return BREAK_TIME
        return LONG_BREAK_TIME

    # --------------------------
    # DATA
    # --------------------------

    def ensure_today_stats(self):
        stats = self.data.setdefault("stats", {})
        if self.today() not in stats:
            stats[self.today()] = {
                "work_time": 0,
                "break_time": 0,
                "cycles": 0,
                "breaks": 0,
                "tasks_done": 0,
                "updated_at": self.now_iso()
            }

    def load_data(self):
        if not os.path.exists(DATA_FILE):
            self.data = {"stats": {}}
            self.ensure_today_stats()
            self.save_data()
            return

        try:
            with open(DATA_FILE, "r", encoding="utf-8") as f:
                self.data = json.load(f)
        except Exception:
            self.data = {"stats": {}}

        if "stats" not in self.data or not isinstance(self.data["stats"], dict):
            self.data["stats"] = {}

        self.ensure_today_stats()

    def save_data(self):
        self.ensure_today_stats()
        self.data["stats"][self.today()]["updated_at"] = self.now_iso()

        with open(DATA_FILE, "w", encoding="utf-8") as f:
            json.dump(self.data, f, indent=2, ensure_ascii=False)

    def today_stats(self):
        self.ensure_today_stats()
        return self.data["stats"][self.today()]

    # --------------------------
    # TASKS
    # --------------------------

    def load_tasks(self):
        if not os.path.exists(TASK_FILE):
            with open(TASK_FILE, "w", encoding="utf-8") as f:
                json.dump([], f, indent=2, ensure_ascii=False)

        try:
            with open(TASK_FILE, "r", encoding="utf-8") as f:
                self.tasks = json.load(f)
        except Exception:
            self.tasks = []

        if not isinstance(self.tasks, list):
            self.tasks = []

        changed = False
        for task in self.tasks:
            if "id" not in task:
                task["id"] = str(uuid.uuid4())
                changed = True
            if "text" not in task:
                task["text"] = ""
                changed = True
            if "done" not in task:
                task["done"] = False
                changed = True
            if "created" not in task:
                task["created"] = self.now_iso()
                changed = True

        if changed:
            self.save_tasks()

    def save_tasks(self):
        with open(TASK_FILE, "w", encoding="utf-8") as f:
            json.dump(self.tasks, f, indent=2, ensure_ascii=False)

    def refresh_tasks(self):
        self.task_list.delete(0, tk.END)

        for task in self.tasks:
            text = task["text"]
            if task["done"]:
                text = "✔ " + text
            self.task_list.insert(tk.END, text)

    def add_task(self):
        text = self.task_entry.get().strip()
        if not text:
            return

        self.tasks.append({
            "id": str(uuid.uuid4()),
            "text": text,
            "done": False,
            "created": self.now_iso()
        })

        self.task_entry.delete(0, tk.END)
        self.refresh_tasks()
        self.save_tasks()
        self.update_ui()

    def toggle_task(self):
        selection = self.task_list.curselection()
        if not selection:
            return

        index = selection[0]
        old_value = self.tasks[index]["done"]
        self.tasks[index]["done"] = not old_value

        if not old_value and self.tasks[index]["done"]:
            self.today_stats()["tasks_done"] += 1
        elif old_value and not self.tasks[index]["done"]:
            self.today_stats()["tasks_done"] = max(0, self.today_stats()["tasks_done"] - 1)

        self.refresh_tasks()
        self.save_tasks()
        self.save_data()
        self.update_ui()

    def delete_task(self):
        selection = self.task_list.curselection()
        if not selection:
            return

        index = selection[0]

        if self.tasks[index]["done"]:
            self.today_stats()["tasks_done"] = max(0, self.today_stats()["tasks_done"] - 1)

        del self.tasks[index]
        self.refresh_tasks()
        self.save_tasks()
        self.save_data()
        self.update_ui()

    # --------------------------
    # UI
    # --------------------------

    def build_ui(self):
        self.root.configure(bg="#f3f4f6")

        style = ttk.Style()
        try:
            style.theme_use("clam")
        except Exception:
            pass

        style.configure("Main.TFrame", background="#f3f4f6")
        style.configure("Card.TLabelframe", background="#ffffff")
        style.configure("Card.TLabelframe.Label", background="#ffffff", font=("Segoe UI", 10, "bold"))
        style.configure("Card.TFrame", background="#ffffff")
        style.configure("Info.TLabel", background="#ffffff", font=("Segoe UI", 11))
        style.configure("Title.TLabel", background="#f3f4f6", font=("Segoe UI", 15, "bold"))
        style.configure("Timer.TLabel", background="#ffffff", font=("Segoe UI", 40, "bold"))
        style.configure("Mode.TLabel", background="#ffffff", font=("Segoe UI", 14, "bold"))
        style.configure("TRadiobutton", background="#ffffff", font=("Segoe UI", 10))
        style.configure("TButton", font=("Segoe UI", 9), padding=4)

        main = ttk.Frame(self.root, style="Main.TFrame", padding=14)
        main.pack(fill="both", expand=True)

        ttk.Label(main, text="Pomodoro Tracker", style="Title.TLabel").pack(anchor="w", pady=(0, 10))

        timer_card = ttk.LabelFrame(main, text="Таймер", style="Card.TLabelframe", padding=14)
        timer_card.pack(fill="x", pady=(0, 10))

        self.mode_label = ttk.Label(timer_card, text="", style="Mode.TLabel")
        self.mode_label.pack()

        self.mode_hint_label = ttk.Label(timer_card, text="", style="Info.TLabel")
        self.mode_hint_label.pack(pady=(3, 0))

        self.timer_label = ttk.Label(timer_card, text="", style="Timer.TLabel")
        self.timer_label.pack(pady=(8, 8))

        self.progress_canvas = tk.Canvas(
            timer_card,
            height=12,
            bg="#ffffff",
            highlightthickness=0
        )
        self.progress_canvas.pack(fill="x", pady=(0, 10))

        mode_select_frame = ttk.Frame(timer_card, style="Card.TFrame")
        mode_select_frame.pack(fill="x", pady=(0, 10))

        self.control_mode_var = tk.StringVar(value=self.control_mode)

        ttk.Radiobutton(
            mode_select_frame,
            text="Автоматический",
            value="automatic",
            variable=self.control_mode_var,
            command=self.change_control_mode
        ).pack(side="left", padx=(0, 12))

        ttk.Radiobutton(
            mode_select_frame,
            text="Ручной",
            value="manual",
            variable=self.control_mode_var,
            command=self.change_control_mode
        ).pack(side="left")

        duration_frame = ttk.Frame(timer_card, style="Card.TFrame")
        duration_frame.pack(fill="x", pady=(0, 10))

        ttk.Label(duration_frame, text="Работа (мин):", style="Info.TLabel").grid(row=0, column=0, sticky="w")
        ttk.Label(duration_frame, text="Перерыв (мин):", style="Info.TLabel").grid(row=0, column=2, sticky="w", padx=(12, 0))
        ttk.Label(duration_frame, text="Длинный (мин):", style="Info.TLabel").grid(row=0, column=4, sticky="w", padx=(12, 0))

        self.work_minutes_var = tk.StringVar(value=str(WORK_TIME // 60))
        self.break_minutes_var = tk.StringVar(value=str(BREAK_TIME // 60))
        self.long_break_minutes_var = tk.StringVar(value=str(LONG_BREAK_TIME // 60))

        ttk.Entry(duration_frame, textvariable=self.work_minutes_var, width=6).grid(row=0, column=1, padx=(6, 0))
        ttk.Entry(duration_frame, textvariable=self.break_minutes_var, width=6).grid(row=0, column=3, padx=(6, 0))
        ttk.Entry(duration_frame, textvariable=self.long_break_minutes_var, width=6).grid(row=0, column=5, padx=(6, 0))

        ttk.Button(duration_frame, text="Применить", command=self.apply_custom_durations).grid(row=0, column=6, padx=(12, 0))

        buttons_frame = ttk.Frame(timer_card, style="Card.TFrame")
        buttons_frame.pack(fill="x")

        for i in range(3):
            buttons_frame.grid_columnconfigure(i, weight=1)

        ttk.Button(buttons_frame, text="Старт", command=self.start).grid(row=0, column=0, sticky="ew", padx=3, pady=3)
        ttk.Button(buttons_frame, text="Пауза", command=self.pause).grid(row=0, column=1, sticky="ew", padx=3, pady=3)
        ttk.Button(buttons_frame, text="Сброс", command=self.reset).grid(row=0, column=2, sticky="ew", padx=3, pady=3)

        ttk.Button(buttons_frame, text="Работа", command=self.set_work).grid(row=1, column=0, sticky="ew", padx=3, pady=3)
        ttk.Button(buttons_frame, text="Перерыв", command=self.set_break).grid(row=1, column=1, sticky="ew", padx=3, pady=3)
        ttk.Button(buttons_frame, text="Длинный", command=self.set_long_break).grid(row=1, column=2, sticky="ew", padx=3, pady=3)


        buttons_frame.grid_columnconfigure(0, weight=1)
        buttons_frame.grid_columnconfigure(1, weight=1)
        buttons_frame.grid_columnconfigure(2, weight=1)

        ttk.Button(buttons_frame, text="Статистика", command=self.open_stats_window).grid(row=2, column=0, columnspan=3, sticky="ew", padx=3, pady=(8, 3))

        self.manual_hint_label = ttk.Label(timer_card, text="", style="Info.TLabel")
        self.manual_hint_label.pack(anchor="w", pady=(10, 0))

        tasks_card = ttk.LabelFrame(main, text="Задачи", style="Card.TLabelframe", padding=14)
        tasks_card.pack(fill="both", expand=True)

        entry_frame = ttk.Frame(tasks_card, style="Card.TFrame")
        entry_frame.pack(fill="x", pady=(0, 8))

        self.task_entry = ttk.Entry(entry_frame)
        self.task_entry.pack(side="left", fill="x", expand=True, padx=(0, 8))
        self.task_entry.bind("<Return>", lambda event: self.add_task())

        ttk.Button(entry_frame, text="Добавить", command=self.add_task, width=12).pack(side="left")

        list_frame = ttk.Frame(tasks_card, style="Card.TFrame")
        list_frame.pack(fill="both", expand=True)

        scrollbar = ttk.Scrollbar(list_frame)
        scrollbar.pack(side="right", fill="y")

        self.task_list = tk.Listbox(
            list_frame,
            yscrollcommand=scrollbar.set,
            font=("Segoe UI", 11),
            activestyle="none",
            bg="#ffffff",
            fg="#111827",
            selectbackground="#dbeafe",
            selectforeground="#111827",
            highlightthickness=1,
            highlightbackground="#d1d5db",
            bd=0
        )
        self.task_list.pack(side="left", fill="both", expand=True)
        scrollbar.config(command=self.task_list.yview)

        task_buttons = ttk.Frame(tasks_card, style="Card.TFrame")
        task_buttons.pack(fill="x", pady=(8, 0))

        ttk.Button(task_buttons, text="Отметить выполненной", command=self.toggle_task, width=22).pack(side="left", padx=(0, 8))
        ttk.Button(task_buttons, text="Удалить", command=self.delete_task, width=12).pack(side="left")

        bottom = ttk.Frame(main, style="Main.TFrame")
        bottom.pack(fill="x", pady=(10, 0))

        self.status_label = ttk.Label(bottom, text="Готово", style="Title.TLabel")
        self.status_label.pack(side="right")

        self.refresh_tasks()

    # --------------------------
    # STATS WINDOW
    # --------------------------

    def open_stats_window(self):
        if self.stats_window is not None and self.stats_window.winfo_exists():
            self.stats_window.lift()
            self.stats_window.focus_force()
            self.update_stats_window()
            return

        self.stats_window = tk.Toplevel(self.root)
        self.stats_window.title("Статистика")
        self.stats_window.geometry("420x320")
        self.stats_window.resizable(False, False)
        self.stats_window.configure(bg="#f3f4f6")

        container = ttk.Frame(self.stats_window, style="Main.TFrame", padding=14)
        container.pack(fill="both", expand=True)

        card = ttk.LabelFrame(container, text="Статистика за сегодня", style="Card.TLabelframe", padding=14)
        card.pack(fill="both", expand=True)

        self.stats_labels["work"] = ttk.Label(card, text="", style="Info.TLabel")
        self.stats_labels["break"] = ttk.Label(card, text="", style="Info.TLabel")
        self.stats_labels["cycles"] = ttk.Label(card, text="", style="Info.TLabel")
        self.stats_labels["breaks"] = ttk.Label(card, text="", style="Info.TLabel")
        self.stats_labels["tasks_done"] = ttk.Label(card, text="", style="Info.TLabel")
        self.stats_labels["mode"] = ttk.Label(card, text="", style="Info.TLabel")

        self.stats_labels["work"].pack(anchor="w", pady=3)
        self.stats_labels["break"].pack(anchor="w", pady=3)
        self.stats_labels["cycles"].pack(anchor="w", pady=3)
        self.stats_labels["breaks"].pack(anchor="w", pady=3)
        self.stats_labels["tasks_done"].pack(anchor="w", pady=3)
        self.stats_labels["mode"].pack(anchor="w", pady=3)

        button_row = ttk.Frame(container, style="Main.TFrame")
        button_row.pack(fill="x", pady=(10, 0))

        ttk.Button(button_row, text="Построить график", command=self.show_graph, width=18).pack(side="left")
        ttk.Button(button_row, text="Закрыть", command=self.stats_window.destroy, width=12).pack(side="right")

        self.update_stats_window()

    def update_stats_window(self):
        if self.stats_window is None or not self.stats_window.winfo_exists():
            return

        stats = self.today_stats()

        self.stats_labels["work"].config(text=f"Время работы: {self.format_hms(stats['work_time'])}")
        self.stats_labels["break"].config(text=f"Время отдыха: {self.format_hms(stats['break_time'])}")
        self.stats_labels["cycles"].config(text=f"Завершённые циклы Pomodoro: {stats['cycles']}")
        self.stats_labels["breaks"].config(text=f"Количество перерывов: {stats['breaks']}")
        self.stats_labels["tasks_done"].config(text=f"Выполненные задачи: {stats['tasks_done']}")
        self.stats_labels["mode"].config(text=f"Активный режим: {self.control_mode_text()}")

    # --------------------------
    # DURATIONS / MODE SWITCH
    # --------------------------

    def get_configured_durations(self):
        work = max(1, self.safe_int(self.work_minutes_var.get(), 25)) * 60
        brk = max(1, self.safe_int(self.break_minutes_var.get(), 5)) * 60
        long_brk = max(1, self.safe_int(self.long_break_minutes_var.get(), 15)) * 60
        return work, brk, long_brk

    def apply_custom_durations(self):
        work, brk, long_brk = self.get_configured_durations()

        global WORK_TIME, BREAK_TIME, LONG_BREAK_TIME
        WORK_TIME = work
        BREAK_TIME = brk
        LONG_BREAK_TIME = long_brk

        if self.control_mode == "automatic" and not self.running:
            self.period_duration = self.default_duration_for_mode(self.mode)
            self.time_left = self.period_duration

        self.status("Длительности обновлены")
        self.update_ui()

    def change_control_mode(self):
        previous_mode = self.control_mode
        self.control_mode = self.control_mode_var.get()
        self.running = False

        if self.control_mode == "automatic":
            self.period_duration = self.default_duration_for_mode(self.mode)
            self.time_left = self.period_duration
        else:
            self.manual_elapsed = 0

        self.status(f"Режим управления: {self.control_mode_text()}")
        if previous_mode != self.control_mode:
            self.update_ui()

    def set_work(self):
        self.running = False
        self.mode = "work"
        if self.control_mode == "automatic":
            self.period_duration = self.default_duration_for_mode(self.mode)
            self.time_left = self.period_duration
        else:
            self.manual_elapsed = 0
        self.status("Выбран режим: работа")
        self.update_ui()

    def set_break(self):
        self.running = False
        self.mode = "break"
        if self.control_mode == "automatic":
            self.period_duration = self.default_duration_for_mode(self.mode)
            self.time_left = self.period_duration
        else:
            self.manual_elapsed = 0
        self.status("Выбран режим: перерыв")
        self.update_ui()

    def set_long_break(self):
        self.running = False
        self.mode = "long_break"
        if self.control_mode == "automatic":
            self.period_duration = self.default_duration_for_mode(self.mode)
            self.time_left = self.period_duration
        else:
            self.manual_elapsed = 0
        self.status("Выбран режим: длинный перерыв")
        self.update_ui()

    # --------------------------
    # TIMER CONTROL
    # --------------------------

    def start(self):
        if self.running:
            return

        self.apply_custom_durations()
        self.running = True
        self.last_update = time.time()
        self.status("Таймер запущен")
        self.tick()

    def pause(self):
        if not self.running:
            return

        self.running = False
        self.status("Пауза")
        self.update_ui()

    def reset(self):
        self.running = False

        if self.control_mode == "automatic":
            self.period_duration = self.default_duration_for_mode(self.mode)
            self.time_left = self.period_duration
            self.status("Таймер сброшен")
        else:
            self.manual_elapsed = 0
            self.status("Секундомер сброшен")

        self.update_ui()

    def tick(self):
        if not self.running:
            return

        now = time.time()
        delta = int(now - self.last_update)

        if delta > 0:
            self.last_update = now

            for _ in range(delta):
                if not self.running:
                    break
                self.process_one_second()

        self.update_ui()
        self.root.after(200, self.tick)

    def process_one_second(self):
        stats = self.today_stats()

        if self.mode == "work":
            stats["work_time"] += 1
        else:
            stats["break_time"] += 1

        if self.control_mode == "automatic":
            self.time_left -= 1
            if self.time_left <= 0:
                self.finish_period_automatic()
        else:
            self.manual_elapsed += 1

        if (stats["work_time"] + stats["break_time"]) % 5 == 0:
            self.save_data()

    def finish_period_automatic(self):
        stats = self.today_stats()

        if self.mode == "work":
            stats["cycles"] += 1
            stats["breaks"] += 1

            if stats["cycles"] % 4 == 0:
                self.mode = "long_break"
                self.period_duration = LONG_BREAK_TIME
                self.time_left = LONG_BREAK_TIME
                self.notify("Время длинного перерыва")
                self.status("Автопереключение: длинный перерыв")
            else:
                self.mode = "break"
                self.period_duration = BREAK_TIME
                self.time_left = BREAK_TIME
                self.notify("Время перерыва")
                self.status("Автопереключение: перерыв")
        else:
            self.mode = "work"
            self.period_duration = WORK_TIME
            self.time_left = WORK_TIME
            self.notify("Пора снова работать")
            self.status("Автопереключение: работа")

        self.save_data()
        self.update_ui()

    # --------------------------
    # GRAPH
    # --------------------------

    def show_graph(self):
        stats_dict = self.data.get("stats", {})
        if not stats_dict:
            messagebox.showinfo("График", "Нет данных для построения графика.")
            return

        days = sorted(stats_dict.keys())
        work_hours = [stats_dict[d]["work_time"] / 3600 for d in days]
        tasks_done = [stats_dict[d]["tasks_done"] for d in days]

        plt.figure(figsize=(10, 5))
        plt.plot(days, work_hours, marker="o", linewidth=2, label="Часы работы")
        plt.plot(days, tasks_done, marker="o", linewidth=2, label="Выполненные задачи")
        plt.grid(True, linestyle="--", alpha=0.6)
        plt.xticks(rotation=45)
        plt.title("Продуктивность по дням")
        plt.xlabel("Дата")
        plt.ylabel("Значение")
        plt.legend()
        plt.tight_layout()
        plt.show()

    # --------------------------
    # NOTIFICATIONS / STATUS
    # --------------------------

    def notify(self, text):
        try:
            notification.notify(
                title="Pomodoro",
                message=text,
                timeout=5
            )
        except Exception:
            pass

    def status(self, text):
        self.status_label.config(text=text)

    # --------------------------
    # UI UPDATE
    # --------------------------

    def draw_progress(self):
        self.progress_canvas.delete("all")

        width = self.progress_canvas.winfo_width()
        if width <= 1:
            width = 600
        height = 12

        self.progress_canvas.create_rectangle(0, 0, width, height, fill="#e5e7eb", outline="")

        if self.control_mode == "automatic":
            if self.period_duration <= 0:
                progress = 0
            else:
                progress = max(0, min(1, (self.period_duration - self.time_left) / self.period_duration))
            fill_width = int(width * progress)
            self.progress_canvas.create_rectangle(0, 0, fill_width, height, fill=self.get_mode_color(), outline="")
        else:
            progress = (self.manual_elapsed % 60) / 60 if self.running else 0
            fill_width = int(width * progress)
            self.progress_canvas.create_rectangle(0, 0, fill_width, height, fill=self.get_mode_color(), outline="")

    def update_ui(self):
        stats = self.today_stats()

        self.mode_label.config(text=self.mode_text(), foreground=self.get_mode_color())

        if self.control_mode == "automatic":
            self.mode_hint_label.config(text="Обратный отсчёт по Pomodoro с автопереключением")
            self.timer_label.config(text=self.format_ms(max(0, self.time_left)))
            self.manual_hint_label.config(
                text="Автоматический режим: работа и отдых переключаются автоматически, есть уведомления."
            )
        else:
            self.mode_hint_label.config(text="Секундомер без ограничения времени")
            self.timer_label.config(text=self.format_hms(self.manual_elapsed))
            self.manual_hint_label.config(
                text="Ручной режим: время идёт вверх, а момент перехода на перерыв ты выбираешь сам."
            )

        self.draw_progress()
        self.update_stats_window()

    # --------------------------
    # CLOSE
    # --------------------------

    def on_close(self):
        self.save_data()
        self.save_tasks()
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    app = PomodoroApp(root)
    root.mainloop()
