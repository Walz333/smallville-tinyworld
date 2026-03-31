"""Validate ledger export JSON against schemas/ledger-export.schema.yaml."""

import json
import os
import glob

import pytest
import yaml

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SCHEMA_DIR = os.path.join(REPO_ROOT, 'schemas')


def load_yaml_schema(name):
    path = os.path.join(SCHEMA_DIR, name)
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


def find_ledger_export_files():
    pattern = os.path.join(REPO_ROOT, 'runs', '**', 'endpoint_ledger_export_*.json')
    return glob.glob(pattern, recursive=True)


def load_json(path):
    with open(path, 'r', encoding='utf-8') as f:
        raw = json.load(f)
    # Handle endpoint capture wrapper
    if 'body' in raw and 'generatedAtUtc' not in raw:
        return raw['body']
    return raw


class TestLedgerExportSchema:
    """Validate ledger export files against the schema."""

    def test_schema_exists(self):
        schema = load_yaml_schema('ledger-export.schema.yaml')
        assert schema is not None
        assert schema.get('title') == 'Ledger Export'

    def test_required_fields_defined(self):
        schema = load_yaml_schema('ledger-export.schema.yaml')
        required = schema.get('required', [])
        assert 'generatedAtUtc' in required
        assert 'scenario' in required
        assert 'world' in required
        assert 'governanceLedger' in required
        assert 'memoryIndex' in required

    @pytest.mark.skipif(
        not find_ledger_export_files(),
        reason='No ledger export captures found in runs/'
    )
    def test_captured_exports_have_required_fields(self):
        try:
            from jsonschema import validate, ValidationError
        except ImportError:
            pytest.skip('jsonschema not installed')

        schema = load_yaml_schema('ledger-export.schema.yaml')
        for path in find_ledger_export_files():
            data = load_json(path)
            try:
                validate(instance=data, schema=schema)
            except ValidationError as e:
                pytest.fail(f'{path}: {e.message}')

    @pytest.mark.skipif(
        not find_ledger_export_files(),
        reason='No ledger export captures found in runs/'
    )
    def test_governance_ledger_is_list(self):
        for path in find_ledger_export_files():
            data = load_json(path)
            if 'governanceLedger' in data:
                assert isinstance(data['governanceLedger'], list), \
                    f'{path}: governanceLedger should be a list'

    @pytest.mark.skipif(
        not find_ledger_export_files(),
        reason='No ledger export captures found in runs/'
    )
    def test_memory_index_keys_are_agent_names(self):
        for path in find_ledger_export_files():
            data = load_json(path)
            if 'memoryIndex' in data and data['memoryIndex']:
                for agent_name, index in data['memoryIndex'].items():
                    assert isinstance(agent_name, str)
                    assert 'agentName' in index or 'totalEvents' in index, \
                        f'{path}: memoryIndex entry for {agent_name} missing expected fields'
