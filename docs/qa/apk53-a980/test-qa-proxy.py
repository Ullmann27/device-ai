import ast
from pathlib import Path
import unittest
import urllib.parse

SOURCE = Path(__file__).with_name('run-android-qa.py')


def load_options():
    tree = ast.parse(SOURCE.read_text())
    functions = [node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'gradle_options']
    if functions:
        module = ast.Module(body=functions, type_ignores=[])
        scope = {'urllib': urllib, 'pathlib': __import__('pathlib')}
        exec(compile(module, str(SOURCE), 'exec'), scope)
        return scope['gradle_options']
    assignments = [node for node in tree.body if isinstance(node, ast.Assign) and any(
        isinstance(t, ast.Name) and t.id == 'proxy' or
        isinstance(t, ast.Subscript) and isinstance(t.value, ast.Name) and t.value.id == 'env' and isinstance(t.slice, ast.Constant) and t.slice.value == 'GRADLE_OPTS'
        for t in node.targets)]
    module = ast.Module(body=assignments, type_ignores=[])
    def old_options(env):
        scope = {'env': dict(env), 'urllib': urllib}
        exec(compile(module, str(SOURCE), 'exec'), scope)
        return scope['env']['GRADLE_OPTS']
    return old_options


class ProxyOptionsTest(unittest.TestCase):
    def setUp(self):
        self.options = load_options()

    def test_no_proxy(self):
        result = self.options({})
        self.assertNotIn('proxyHost', result)
        self.assertNotIn('proxyPort', result)
        self.assertNotIn('None', result)

    def test_http_explicit_port(self):
        result = self.options({'HTTP_PROXY': 'http://proxy.test:8080'})
        self.assertIn('-Dhttp.proxyHost=proxy.test', result)
        self.assertIn('-Dhttps.proxyPort=8080', result)

    def test_https_default_port(self):
        self.assertIn('-Dhttps.proxyPort=443', self.options({'HTTPS_PROXY': 'https://proxy.test'}))

    def test_http_default_port(self):
        self.assertIn('-Dhttp.proxyPort=80', self.options({'HTTP_PROXY': 'http://proxy.test'}))

    def test_empty_https_uses_http(self):
        self.assertIn('-Dhttp.proxyPort=8080', self.options({'HTTPS_PROXY': '', 'HTTP_PROXY': 'http://proxy.test:8080'}))

    def test_existing_options_retained(self):
        self.assertIn('-Dfile.encoding=UTF-8', self.options({'GRADLE_OPTS': '-Dfile.encoding=UTF-8'}))

    def test_credentials_not_emitted(self):
        result = self.options({'HTTPS_PROXY': 'https://fixture-user:fixture-password@proxy.test:8443'})
        self.assertNotIn('fixture-user', result)
        self.assertNotIn('fixture-password', result)
        self.assertIn('-Dhttps.proxyPort=8443', result)

    def test_malformed_proxy_rejected(self):
        with self.assertRaises(ValueError):
            self.options({'HTTPS_PROXY': 'not-a-proxy-url'})


if __name__ == '__main__':
    unittest.main()
