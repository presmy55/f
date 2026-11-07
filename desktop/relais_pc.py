import secrets
import socket
import threading
import tkinter as tk
from tkinter import messagebox, ttk


PORT = 5005
KEYS = {
    0x11: "w",
    0x1E: "a",
    0x1F: "s",
    0x20: "d",
    0x39: " ",
}
BUTTONS = {1: "left", 2: "right"}


def local_ip():
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 80))
        return probe.getsockname()[0]
    except OSError:
        return "Adresse indisponible"
    finally:
        probe.close()


def handle_packet(packet, pin, keyboard, mouse, mouse_button_type, pressed_keys, pressed_buttons):
    try:
        fields = packet.decode("ascii").split()
        if len(fields) < 3 or not secrets.compare_digest(fields[0], pin):
            return

        if fields[1] == "K" and len(fields) == 4:
            key = KEYS.get(int(fields[2]))
            action = fields[3]
            if key is None or action not in ("down", "up"):
                return
            if action == "down" and key not in pressed_keys:
                keyboard.press(key)
                pressed_keys.add(key)
            elif action == "up" and key in pressed_keys:
                keyboard.release(key)
                pressed_keys.remove(key)
        elif fields[1] == "B" and len(fields) == 4:
            button = BUTTONS.get(int(fields[2]))
            action = fields[3]
            if button is None or action not in ("down", "up"):
                return
            if action == "down" and button not in pressed_buttons:
                mouse.press(getattr(mouse_button_type, button))
                pressed_buttons.add(button)
            elif action == "up" and button in pressed_buttons:
                mouse.release(getattr(mouse_button_type, button))
                pressed_buttons.remove(button)
        elif fields[1] == "M" and len(fields) == 4:
            dx, dy = int(fields[2]), int(fields[3])
            if -127 <= dx <= 127 and -127 <= dy <= 127:
                mouse.move(dx, dy)
    except (UnicodeDecodeError, ValueError):
        return


class RelaisPc:
    def __init__(self, root):
        self.root = root
        self.server = None
        self.running = False
        self.pin = tk.StringVar(value=str(secrets.randbelow(900000) + 100000))
        self.status = tk.StringVar(value="Arrêté")
        self.address = tk.StringVar(value=f"{local_ip()}:{PORT}")

        root.title("Relais · Clavier et souris")
        root.geometry("420x390")
        root.minsize(360, 350)
        root.configure(background="#f4f4ef")
        root.protocol("WM_DELETE_WINDOW", self.close)

        style = ttk.Style()
        style.theme_use("clam")
        style.configure("TFrame", background="#f4f4ef")
        style.configure("TLabel", background="#f4f4ef", foreground="#202b27", font=("TkDefaultFont", 11))
        style.configure("Title.TLabel", font=("TkDefaultFont", 19, "bold"), foreground="#173b35")
        style.configure("Value.TLabel", font=("TkDefaultFont", 15, "bold"))
        style.configure("TButton", font=("TkDefaultFont", 11), padding=9)
        style.configure("Start.TButton", background="#246b58", foreground="white")

        page = ttk.Frame(root, padding=24)
        page.pack(fill="both", expand=True)
        ttk.Label(page, text="Relais", style="Title.TLabel").pack(anchor="w")
        ttk.Label(page, text="Clavier et souris depuis votre téléphone").pack(anchor="w", pady=(2, 20))
        ttk.Label(page, text="Adresse de cet ordinateur").pack(anchor="w")
        ttk.Label(page, textvariable=self.address, style="Value.TLabel").pack(anchor="w", pady=(3, 14))
        ttk.Label(page, text="Code à saisir dans le téléphone").pack(anchor="w")

        pin_row = ttk.Frame(page)
        pin_row.pack(fill="x", pady=(4, 18))
        self.pin_entry = ttk.Entry(pin_row, textvariable=self.pin, font=("TkDefaultFont", 15), justify="center")
        self.pin_entry.pack(side="left", fill="x", expand=True, ipady=5)
        ttk.Button(pin_row, text="Nouveau code", command=self.new_pin).pack(side="left", padx=(8, 0))

        ttk.Label(page, text="État du serveur").pack(anchor="w")
        ttk.Label(page, textvariable=self.status, style="Value.TLabel").pack(anchor="w", pady=(3, 14))
        self.start_button = ttk.Button(page, text="Démarrer", style="Start.TButton", command=self.start)
        self.start_button.pack(fill="x")
        ttk.Label(
            page,
            text="Réseau privé seulement. Arrêtez le serveur après utilisation.",
            wraplength=360,
            foreground="#6b5140",
        ).pack(anchor="w", pady=(16, 0))

    def new_pin(self):
        if not self.running:
            self.pin.set(str(secrets.randbelow(900000) + 100000))

    def start(self):
        pin = self.pin.get().strip()
        if not pin.isdigit() or not 4 <= len(pin) <= 6:
            messagebox.showerror("Code invalide", "Le code doit contenir de 4 à 6 chiffres.")
            return
        self.running = True
        self.start_button.configure(text="Arrêter", command=self.stop)
        self.status.set("Démarrage…")
        threading.Thread(target=self.serve, args=(pin,), daemon=True).start()

    def serve(self, pin):
        try:
            from pynput import keyboard, mouse

            server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            server.settimeout(0.5)
            server.bind(("0.0.0.0", PORT))
            self.server = server
            self.root.after(0, lambda: self.status.set(f"En écoute sur le port {PORT}"))
            pressed_keys = set()
            pressed_buttons = set()
            key_controller = keyboard.Controller()
            mouse_controller = mouse.Controller()
            while self.running:
                try:
                    packet, _ = server.recvfrom(1024)
                except socket.timeout:
                    continue
                except OSError:
                    break
                handle_packet(
                    packet,
                    pin,
                    key_controller,
                    mouse_controller,
                    mouse.Button,
                    pressed_keys,
                    pressed_buttons,
                )
            for key in pressed_keys:
                key_controller.release(key)
            for button in pressed_buttons:
                mouse_controller.release(getattr(mouse.Button, button))
            server.close()
        except Exception as error:
            self.root.after(0, lambda message=str(error): self.show_error(message))
        finally:
            self.server = None
            self.running = False
            self.root.after(0, self.stopped)

    def show_error(self, error):
        messagebox.showerror(
            "Impossible de démarrer",
            f"{error}\n\nVérifiez les dépendances, le pare-feu et le port {PORT}.",
        )

    def stop(self):
        self.running = False
        if self.server:
            self.server.close()
        self.status.set("Arrêt en cours…")

    def stopped(self):
        self.start_button.configure(text="Démarrer", command=self.start)
        self.status.set("Arrêté")

    def close(self):
        self.stop()
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    RelaisPc(root)
    root.mainloop()