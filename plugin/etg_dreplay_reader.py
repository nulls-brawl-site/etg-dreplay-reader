import hashlib
import os
import time
import urllib.request

from android_utils import copy_to_clipboard
from base_plugin import BasePlugin, MethodHook
from client_utils import PLUGINS_QUEUE, run_on_queue
from file_utils import ensure_dir_exists, get_plugins_dir
from java import jarray, jbyte, jclass, jint
from java.lang import Boolean, ClassLoader
from ui.settings import Divider, Header, Text

__id__ = "etg_dreplay_reader"
__name__ = "DReplay Reader"
__description__ = "Opens .dreplay Durak replay files inside ExteraGram."
__author__ = "@bsod4ik_plugins"
__version__ = "1.0.0"
__icon__ = "msg_view_file"
__app_version__ = ">=12.5.1"
__sdk_version__ = ">=1.4.3.3"

ENTRY_CLASS = "com.etgdreplay.reader.DReplayBridge"
DEX_URL = "https://raw.githubusercontent.com/nulls-brawl-site/etg-dreplay-reader/master/build/etg-dreplay-reader.dex"
DEX_SHA256 = "75b06e0c45335d6962753fd0e8cf2fb080eab36c95d884c7acb5c6ba379f6974"


class _BeforeOpenForViewFile(MethodHook):
    def __init__(self, plugin):
        self.plugin = plugin

    def before_hooked_method(self, param):
        if self.plugin.handle_file_object(param.args[0], param.args[1], param.args[3]):
            param.setResult(True)


class _BeforeOpenForViewMessage(MethodHook):
    def __init__(self, plugin):
        self.plugin = plugin

    def before_hooked_method(self, param):
        if self.plugin.handle_message_object(param.args[0], param.args[1]):
            param.setResult(True)


class _BeforeOpenDocument(MethodHook):
    def __init__(self, plugin):
        self.plugin = plugin

    def before_hooked_method(self, param):
        if self.plugin.handle_message_object(param.args[0], param.args[1]):
            param.setResult(None)


class DReplayReaderPlugin(BasePlugin):
    def on_plugin_load(self):
        self._logs = []
        self._bridge = None
        self._bridge_open = None
        self._bridge_ready = False
        self._loading = False
        self._log("plugin loaded")
        self._install_hooks()
        run_on_queue(self._load_bridge, PLUGINS_QUEUE)

    def create_settings(self):
        return [
            Header(text="DReplay Reader"),
            Text(
                text="Открывает .dreplay",
                subtext="Парсер и интерфейс находятся в dex, Python только loader.",
                icon="msg_view_file",
            ),
            Divider(),
            Text(
                text="Копировать логи",
                subtext="Версия, dex, bridge и последние события",
                icon="msg_copy",
                on_click=lambda _v: self._copy_logs(),
            ),
        ]

    def _install_hooks(self):
        AndroidUtilities = self._class_ref("org.telegram.messenger.AndroidUtilities")
        File = self._class_ref("java.io.File")
        String = self._class_ref("java.lang.String")
        Activity = self._class_ref("android.app.Activity")
        ResourcesProvider = self._class_ref("org.telegram.ui.ActionBar.Theme$ResourcesProvider")
        MessageObject = self._class_ref("org.telegram.messenger.MessageObject")
        BaseFragment = self._class_ref("org.telegram.ui.ActionBar.BaseFragment")

        if AndroidUtilities is None:
            self._log("hook failed: AndroidUtilities not found")
            return

        if File is not None and String is not None and Activity is not None and ResourcesProvider is not None:
            self._hook_declared(
                AndroidUtilities,
                "openForView",
                _BeforeOpenForViewFile(self),
                File,
                String,
                String,
                Activity,
                ResourcesProvider,
                Boolean.TYPE,
            )

        if MessageObject is not None and Activity is not None and ResourcesProvider is not None:
            self._hook_declared(
                AndroidUtilities,
                "openForView",
                _BeforeOpenForViewMessage(self),
                MessageObject,
                Activity,
                ResourcesProvider,
                Boolean.TYPE,
            )

        if MessageObject is not None and Activity is not None and BaseFragment is not None:
            self._hook_declared(
                AndroidUtilities,
                "openDocument",
                _BeforeOpenDocument(self),
                MessageObject,
                Activity,
                BaseFragment,
            )

    def _load_bridge(self):
        if self._loading:
            return
        self._loading = True
        try:
            data = self._ensure_dex_bytes()
            if not data:
                self._log("dex bytes unavailable")
                return
            ctx = jclass("org.telegram.messenger.ApplicationLoader").applicationContext
            parent = ctx.getClassLoader() or ClassLoader.getSystemClassLoader()
            loader = self._make_loader(data, parent)
            self._bridge = loader.loadClass(ENTRY_CLASS)
            Object = self._class_ref("java.lang.Object")
            String = self._class_ref("java.lang.String")
            self._bridge_open = self._bridge.getDeclaredMethod("openFile", Object, String, String)
            self._bridge_open.setAccessible(True)
            self._bridge_ready = True
            self._log("dex bridge loaded")
        except Exception as e:
            self._bridge_ready = False
            self._log(f"dex load failed: {e}")
        finally:
            self._loading = False

    def _make_loader(self, data, parent):
        try:
            Build = jclass("android.os.Build")
            if int(Build.VERSION.SDK_INT) >= 26:
                ByteBuffer = jclass("java.nio.ByteBuffer")
                InMemoryDexClassLoader = jclass("dalvik.system.InMemoryDexClassLoader")
                signed = [(b - 256 if b > 127 else b) for b in data]
                buf = ByteBuffer.wrap(jarray(jbyte)(signed))
                self._log("loading dex in memory")
                return InMemoryDexClassLoader(buf, parent)
        except Exception as e:
            self._log(f"in-memory dex unavailable: {e}")

        dex_path = self._dex_path()
        self._write_read_only(dex_path, data)
        opt_dir = os.path.join(self._dex_dir(), "dex_opt")
        ensure_dir_exists(opt_dir)
        DexClassLoader = jclass("dalvik.system.DexClassLoader")
        self._log(f"loading dex from {dex_path}")
        return DexClassLoader(dex_path, opt_dir, None, parent)

    def handle_file_object(self, file_obj, display_name, activity):
        try:
            path = str(file_obj.getAbsolutePath()) if file_obj is not None else ""
            name = str(display_name or "")
            return self._open_dreplay(path, name, activity)
        except Exception as e:
            self._log(f"file open hook failed: {e}")
            return False

    def handle_message_object(self, message_obj, activity):
        try:
            name = self._message_name(message_obj)
            if not self._is_dreplay(name):
                return False
            file_obj = self._message_file(message_obj)
            if file_obj is None or not file_obj.exists():
                self._log(f"message file not ready: {name}")
                return False
            return self._open_dreplay(str(file_obj.getAbsolutePath()), name, activity)
        except Exception as e:
            self._log(f"message open hook failed: {e}")
            return False

    def _open_dreplay(self, path, name, activity):
        if not (self._is_dreplay(path) or self._is_dreplay(name)):
            return False
        if not self._bridge_ready or self._bridge_open is None:
            self._log("bridge not ready for .dreplay")
            run_on_queue(self._load_bridge, PLUGINS_QUEUE)
            return False
        try:
            result = self._bridge_open.invoke(None, activity, path or "", name or "")
            return str(result).lower() == "true"
        except Exception as e:
            self._log(f"bridge open failed: {e}")
            return False

    def _message_name(self, message_obj):
        if message_obj is None:
            return ""
        for method_name in ("getFileName", "getDocumentName"):
            try:
                value = getattr(message_obj, method_name)()
                if value:
                    return str(value)
            except Exception:
                pass
        return ""

    def _message_file(self, message_obj):
        if message_obj is None:
            return None
        try:
            account = int(getattr(message_obj, "currentAccount"))
        except Exception:
            account = 0
        try:
            FileLoader = self._class_ref("org.telegram.messenger.FileLoader")
            Message = self._class_ref("org.telegram.tgnet.TLRPC$Message")
            Integer = jclass("java.lang.Integer")
            get_instance = FileLoader.getDeclaredMethod("getInstance", Integer.TYPE)
            get_instance.setAccessible(True)
            loader = get_instance.invoke(None, self._jint(account))
            get_path = FileLoader.getDeclaredMethod("getPathToMessage", Message)
            get_path.setAccessible(True)
            return get_path.invoke(loader, message_obj.messageOwner)
        except Exception as e:
            self._log(f"message path failed: {e}")
            return None

    def _ensure_dex_bytes(self):
        dex_path = self._dex_path()
        ensure_dir_exists(self._dex_dir())
        if os.path.exists(dex_path) and os.path.getsize(dex_path) > 1024:
            try:
                if self._sha256(dex_path) == DEX_SHA256:
                    self._log(f"using cached dex at {dex_path}")
                    with open(dex_path, "rb") as f:
                        return f.read()
                self._log("cached dex sha mismatch")
            except Exception as e:
                self._log(f"cached dex check failed: {e}")

        tmp_path = dex_path + ".tmp"
        try:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
            with urllib.request.urlopen(DEX_URL, timeout=25) as response:
                data = response.read()
            if len(data) < 1024:
                self._log("dex download failed: response too small")
                return None
            got_sha = hashlib.sha256(data).hexdigest()
            if got_sha != DEX_SHA256:
                self._log(f"dex sha mismatch: {got_sha}")
                return None
            self._write_read_only(dex_path, data, tmp_path)
            self._log(f"dex downloaded sha256={got_sha}")
            return data
        except Exception as e:
            self._log(f"dex download failed: {e}")
            return None
        finally:
            try:
                if os.path.exists(tmp_path):
                    os.remove(tmp_path)
            except Exception:
                pass

    def _write_read_only(self, path, data, tmp_path=None):
        tmp = tmp_path or (path + ".tmp")
        try:
            if os.path.exists(tmp):
                os.remove(tmp)
            with open(tmp, "wb") as f:
                f.write(data)
            self._make_read_only(tmp)
            os.replace(tmp, path)
            self._make_read_only(path)
        finally:
            try:
                if os.path.exists(tmp):
                    os.remove(tmp)
            except Exception:
                pass

    def _is_dreplay(self, value):
        return bool(value) and str(value).lower().endswith(".dreplay")

    def _dex_dir(self):
        return os.path.join(get_plugins_dir(), __id__)

    def _dex_path(self):
        return os.path.join(self._dex_dir(), "etg-dreplay-reader.dex")

    def _class_ref(self, name):
        try:
            ctx = jclass("org.telegram.messenger.ApplicationLoader").applicationContext
            loader = ctx.getClassLoader()
            if loader is not None:
                return loader.loadClass(name)
        except Exception:
            pass
        try:
            Class = jclass("java.lang.Class")
            return Class.forName(name)
        except Exception:
            pass
        try:
            cls = jclass(name)
            if hasattr(cls, "getDeclaredMethod"):
                return cls
            if hasattr(cls, "class_"):
                return cls.class_
        except Exception:
            return None
        return None

    def _hook_declared(self, clazz, name, hook, *types):
        try:
            method = clazz.getDeclaredMethod(name, *types)
            method.setAccessible(True)
            self.hook_method(method, hook)
            self._log(f"hook installed: {clazz.getName()}.{name}")
            return True
        except Exception as e:
            try:
                self._log(f"hook failed: {clazz.getName()}.{name}: {e}")
            except Exception:
                pass
            return False

    def _copy_logs(self):
        text = self._logs_text()
        try:
            copy_to_clipboard(text)
            self._log("logs copied")
        except Exception as e:
            self._log(f"logs copy failed: {e}")

    def _logs_text(self):
        lines = [
            "DReplay Reader logs",
            f"plugin_version={__version__}",
            f"sdk_version={__sdk_version__}",
            f"dex_url={DEX_URL}",
            f"dex_sha256={DEX_SHA256}",
            f"dex_path={self._dex_path()}",
            f"dex_exists={os.path.exists(self._dex_path())}",
            f"bridge_ready={self._bridge_ready}",
        ]
        try:
            if os.path.exists(self._dex_path()):
                lines.append(f"dex_local_sha256={self._sha256(self._dex_path())}")
        except Exception as e:
            lines.append(f"dex_local_sha256_error={e}")
        logs = list(getattr(self, "_logs", []) or [])
        if logs:
            lines.append("")
            lines.append("events:")
            lines.extend(logs[-120:])
        return "\n".join(lines)

    def _log(self, message):
        try:
            logs = getattr(self, "_logs", None)
            if logs is None:
                self._logs = []
                logs = self._logs
            logs.append(f"{time.strftime('%Y-%m-%d %H:%M:%S')} {message}")
            if len(logs) > 120:
                del logs[:-120]
        except Exception:
            pass

    def _jint(self, value):
        return jint(int(value))

    def _make_read_only(self, path):
        try:
            os.chmod(path, 0o444)
            return
        except Exception:
            pass
        try:
            File = jclass("java.io.File")
            File(path).setReadOnly()
        except Exception:
            pass

    def _sha256(self, path):
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1024 * 128), b""):
                h.update(chunk)
        return h.hexdigest()
