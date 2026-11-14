"""Static contracts for the protected signing workflow."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/continuum-code57-original-sign.yml'
SIGN_NAME = 'Verify exact source, sign with original key, retain unpublished draft only'


def signer_step(text):
    pattern = r'(?ms)^      - name: ' + re.escape(SIGN_NAME) + r'\n(.*?)(?=^      - |^  [^ ]|\Z)'
    matches = list(re.finditer(pattern, text))
    if len(matches) != 1:
        raise AssertionError('Exactly one signer step required')
    return matches[0]


class WorkflowScopeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding='utf-8')

    def test_secrets_exist_only_inside_signer_step(self):
        match = signer_step(self.text)
        outside = self.text[:match.start()] + self.text[match.end():]
        self.assertNotIn('secrets.', outside)
        self.assertEqual(len(re.findall(r'\bsecrets\.', match.group())), 2)

    def test_signing_step_receives_both_original_secrets(self):
        body = signer_step(self.text).group(1)
        self.assertIn('\n        env:\n', '\n' + body)
        self.assertRegex(body, r'(?m)^          ORIGINAL_P12: \$\{\{ secrets\.CONTINUUM_LOCAL_SIGNING_P12_BASE64 \}\}$')
        self.assertRegex(body, r'(?m)^          ORIGINAL_PASSWORD: \$\{\{ secrets\.CONTINUUM_LOCAL_SIGNING_PASSWORD \}\}$')

    def test_actions_are_immutable_verified_pins(self):
        uses = re.findall(r'(?m)^\s+- uses: (\S+)', self.text)
        expected = [
            'actions/checkout@11d5960a326750d5838078e36cf38b85af677262',
            'actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3',
        ] * 2
        self.assertCountEqual(uses, expected)

    def test_unused_node_setup_is_absent(self):
        self.assertNotIn('actions/setup-node', self.text)

    def test_protected_owner_gate_is_preserved(self):
        self.assertIn('    environment: continuum-original-signing\n', self.text)
        self.assertIn('    needs: preflight\n', self.text)
        condition = "    if: github.repository == 'Ullmann27/device-ai' && github.ref == 'refs/heads/chatgpt/code52-owner-signing-20261109'"
        self.assertEqual(self.text.count(condition), 2)
        self.assertNotIn('pull_request_target:', self.text)
        self.assertNotIn('workflow_dispatch:', self.text)

    def test_exact_product_and_qa_stay_pinned(self):
        for line in [
            '      SOURCE_SHA: "b61c5f3cd7bad29a17396b0cd4cd62294e365161"',
            '      OWNER_APPROVED_SHA: "b61c5f3cd7bad29a17396b0cd4cd62294e365161"',
            '      QA_RUN_ID: "43480692570"',
        ]:
            self.assertEqual(self.text.count(line), 2)

    def test_checkout_never_persists_credentials(self):
        self.assertEqual(self.text.count('          persist-credentials: false'), 2)

    def test_signer_is_called_only_once(self):
        body = signer_step(self.text).group(1)
        self.assertEqual(self.text.count('bash scripts/continuum-code57-original-signer.sh sign\n'), 1)
        self.assertIn('bash scripts/continuum-code57-original-signer.sh sign\n', body)


if __name__ == '__main__':
    unittest.main(verbosity=2)
