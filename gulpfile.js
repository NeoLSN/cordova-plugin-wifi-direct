const exec = require('child_process').exec
const gulp = require('gulp')

gulp.task(
  'watch',
  () => {
    gulp.watch(
      'www_src/wifi_direct.ts',
      [
        'ts_build',
      ]
    )
  }
)

gulp.task(
  'ts_build',
  cb => {
    exec(
      'npm run ts_build',
      (err, stdout, stderr) => {
        if (err) console.log(err)
        if (stdout) console.log(stdout)
        if (stderr) console.log(stderr)
        cb()
      }
    )
  }
)

gulp.task(
  'default',
  [
    'watch',
  ]
)