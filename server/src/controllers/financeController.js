const financeService = require('../services/financeService');

function handleError(res, err, defaultCode) {
  const status = err.status || 500;
  return res.status(status).json({
    error: err.code || defaultCode,
    message: err.message
  });
}

class FinanceController {
  async getAccounts(req, res) {
    try {
      const result = await financeService.getAccounts(req.user.id);
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_ACCOUNTS_FAILED');
    }
  }

  async createAccount(req, res) {
    try {
      const result = await financeService.createAccount(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      handleError(res, err, 'CREATE_ACCOUNT_FAILED');
    }
  }

  async getCategories(req, res) {
    try {
      const result = await financeService.getCategories(req.user.id);
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_CATEGORIES_FAILED');
    }
  }

  async createCategory(req, res) {
    try {
      const result = await financeService.createCategory(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      handleError(res, err, 'CREATE_CATEGORY_FAILED');
    }
  }

  async getRules(req, res) {
    try {
      const result = await financeService.getRules(req.user.id);
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_RULES_FAILED');
    }
  }

  async createRule(req, res) {
    try {
      const result = await financeService.createRule(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      handleError(res, err, 'CREATE_RULE_FAILED');
    }
  }

  async getTransactions(req, res) {
    try {
      const result = await financeService.getTransactions(req.user.id);
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_TRANSACTIONS_FAILED');
    }
  }

  async getTransactionById(req, res) {
    try {
      const result = await financeService.getTransactionById(req.params.id, req.user.id);
      if (!result) {
        return res.status(404).json({ error: 'NOT_FOUND', message: 'Transaction not found or access denied' });
      }
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_TRANSACTION_FAILED');
    }
  }

  async createTransaction(req, res) {
    try {
      const result = await financeService.createTransaction(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      handleError(res, err, 'CREATE_TRANSACTION_FAILED');
    }
  }

  async getLoans(req, res) {
    try {
      const result = await financeService.getLoans(req.user.id);
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_LOANS_FAILED');
    }
  }

  async createLoan(req, res) {
    try {
      const result = await financeService.createLoan(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      handleError(res, err, 'CREATE_LOAN_FAILED');
    }
  }

  async recordRepayment(req, res) {
    try {
      const result = await financeService.recordRepayment(req.user.id, req.params.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      if (err.message === 'LOAN_NOT_FOUND') {
        return res.status(404).json({ error: 'NOT_FOUND', message: 'Loan record not found or access denied' });
      }
      handleError(res, err, 'REPAYMENT_FAILED');
    }
  }

  async getCompanyExpenses(req, res) {
    try {
      const result = await financeService.getCompanyExpenses(req.user.id);
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_COMPANY_EXPENSES_FAILED');
    }
  }

  async createCompanyExpense(req, res) {
    try {
      const result = await financeService.createCompanyExpense(req.user.id, req.body);
      res.status(201).json(result);
    } catch (err) {
      handleError(res, err, 'CREATE_COMPANY_EXPENSE_FAILED');
    }
  }

  async getDashboardData(req, res) {
    try {
      const result = await financeService.getDashboardData(req.user.id);
      res.json(result);
    } catch (err) {
      handleError(res, err, 'GET_DASHBOARD_FAILED');
    }
  }
}

module.exports = new FinanceController();
