import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location('apply_once', pathlib.Path(__file__).with_name('apply-once.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ComposeLiteralTest(unittest.TestCase):
    def test_entrypoint_and_secret_literals_are_not_host_interpolated(self):
        original = {'entrypoint': ['sh', '-c', 'java $JAVA_OPTS -jar /app/app.jar'],
                    'environment': {'EXAMPLE': 'literal$abc${def}$$'}, 'cpus': 1.0}
        escaped = module.escape_compose(original)
        self.assertEqual(escaped['entrypoint'][2], 'java $$JAVA_OPTS -jar /app/app.jar')
        self.assertEqual(escaped['environment']['EXAMPLE'], 'literal$$abc$${def}$$$$')
        self.assertEqual(escaped['cpus'], 1.0)
        self.assertEqual(original['entrypoint'][2], 'java $JAVA_OPTS -jar /app/app.jar')

if __name__ == '__main__':
    unittest.main()
