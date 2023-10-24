const path = require('path');
const pak = require('../library/package.json');

module.exports = {
  dependencies: {
    [pak.name]: {
      root: path.join(__dirname, '../library'),
    },
  },
};
