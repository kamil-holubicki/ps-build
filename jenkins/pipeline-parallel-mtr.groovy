def pipeline_timeout = 24
def JENKINS_SCRIPTS_BRANCH = 'parallel-fb-aborted-retry'
def JENKINS_SCRIPTS_REPO = 'https://github.com/kamil-holubicki/ps-build'
def WORKER_1_ABORTED = false
def WORKER_2_ABORTED = false
def WORKER_3_ABORTED = false
def WORKER_4_ABORTED = false
def WORKER_5_ABORTED = false
def WORKER_6_ABORTED = false
def WORKER_7_ABORTED = false
def WORKER_8_ABORTED = false
def BUILD_NUMBER_BINARIES_FOR_RERUN = 0
def WORKER_1_LABEL = LABEL

if (
    (params.ANALYZER_OPTS.contains('-DWITH_ASAN=ON')) ||
    (params.ANALYZER_OPTS.contains('-DWITH_UBSAN=ON'))
    ) { pipeline_timeout = 48 }

if (params.ANALYZER_OPTS.contains('-DWITH_VALGRIND=ON'))
    { pipeline_timeout = 144 }

if (
    ((params.ANALYZER_OPTS.contains('-DWITH_ASAN=ON')) &&
    (params.ANALYZER_OPTS.contains('-DWITH_ASAN_SCOPE=ON')) &&
    (params.ANALYZER_OPTS.contains('-DWITH_UBSAN=ON'))) ||
    ((params.MTR_ARGS.contains('--big-test')) || (params.MTR_ARGS.contains('--only-big-test')))
    ) {
        LABEL = 'docker-32gb'
      }

if (
    (params.ZEN_FS_MTR == 'yes') &&
    (params.DOCKER_OS == 'ubuntu:hirsute')
    ) { 
        WORKER_1_LABEL = 'docker-32gb-hirsute'
      }

if (
    (params.ZEN_FS_MTR == 'yes') &&
    (params.DOCKER_OS == 'ubuntu:focal')
    ) { 
        WORKER_1_LABEL = 'docker-32gb-focal'
      }

if (
    (params.ZEN_FS_MTR == 'yes') &&
    (params.DOCKER_OS == 'debian:bullseye')
    ) {
        WORKER_1_LABEL = 'docker-32gb-bullseye'
      }

pipeline {
    parameters {
        string(
            defaultValue: '',
            description: 'Reuse binaries built in the specified build. Useful for quick MTR test rerun without rebuild.',
            name: 'BUILD_NUMBER_BINARIES',
            trim: true)
        string(
            defaultValue: 'https://github.com/percona/percona-server',
            description: 'URL to percona-server repository',
            name: 'GIT_REPO',
            trim: true)
        string(
            defaultValue: '8.0',
            description: 'Tag/Branch for percona-server repository',
            name: 'BRANCH',
            trim: true)
        choice(
            choices: 'ubuntu:focal\ncentos:7\ncentos:8\noraclelinux:9\nubuntu:bionic\nubuntu:jammy\ndebian:buster\ndebian:bullseye',
            description: 'OS version for compilation',
            name: 'DOCKER_OS')
        choice(
            choices: '/usr/bin/cmake',
            description: 'path to cmake binary',
            name: 'JOB_CMAKE')
        choice(
            choices: 'default',
            description: 'compiler version',
            name: 'COMPILER')
        choice(
            choices: 'Debug\nRelWithDebInfo',
            description: 'Type of build to produce',
            name: 'CMAKE_BUILD_TYPE')
        choice(
            choices: '\n-DWITH_ASAN=ON -DWITH_ASAN_SCOPE=ON\n-DWITH_ASAN=ON\n-DWITH_ASAN=ON -DWITH_ASAN_SCOPE=ON -DWITH_UBSAN=ON\n-DWITH_ASAN=ON -DWITH_UBSAN=ON\n-DWITH_UBSAN=ON\n-DWITH_MSAN=ON\n-DWITH_VALGRIND=ON',
            description: 'Enable code checking',
            name: 'ANALYZER_OPTS')
        choice(
            choices: 'ON\nOFF',
            description: 'Compile RocksDB engine',
            name: 'WITH_ROCKSDB')
        choice(
            choices: 'ON\nOFF',
            description: 'Whether to build MySQL Router',
            name: 'WITH_ROUTER')
        choice(
            choices: 'ON\nOFF',
            description: 'Whether to build with support for X Plugin',
            name: 'WITH_MYSQLX')
        choice(
            choices: 'ON\nOFF',
            description: 'Whether to build with support for keyring_vault Plugin',
            name: 'WITH_KEYRING_VAULT')
        string(
            defaultValue: '',
            description: 'cmake options',
            name: 'CMAKE_OPTS')
        string(
            defaultValue: '',
            description: 'make options, like VERBOSE=1',
            name: 'MAKE_OPTS')
        choice(
            choices: 'yes\nno',
            description: 'Run mysql-test-run.pl',
            name: 'DEFAULT_TESTING')
        choice(
            choices: 'no\nyes',
            description: 'Run ZenFS MTR tests',
            name: 'ZEN_FS_MTR')
        choice(
            choices: 'yes\nno',
            description: 'Run case-insensetive MTR tests',
            name: 'CI_FS_MTR')
        string(
            defaultValue: '--unit-tests-report --mem --big-test',
            description: 'mysql-test-run.pl options, for options like: --big-test --only-big-test --nounit-tests --unit-tests-report',
            name: 'MTR_ARGS')
        string(
            defaultValue: '1',
            description: 'Run each test N number of times, --repeat=N',
            name: 'MTR_REPEAT')
        choice(
            choices: 'yes\nno',
            description: 'Run mtr --suite=keyring_vault',
            name: 'KEYRING_VAULT_MTR')
        string(
            defaultValue: '0.9.6',
            description: 'Specifies version of Hashicorp Vault for V1 tests',
            name: 'KEYRING_VAULT_V1_VERSION'
        )
        string(
            defaultValue: '1.9.0',
            description: 'Specifies version of Hashicorp Vault for V2 tests',
            name: 'KEYRING_VAULT_V2_VERSION'
        )
        choice(
            choices: 'docker-32gb\ndocker',
            description: 'Run build on specified instance type',
            name: 'LABEL')
        choice(
            choices: 'yes\nno',
            description: 'Run mtr suites based on variable WORKER_X_MTR_SUITES if the value is `no`. Otherwise the full mtr will be perfomed.',
            name: 'FULL_MTR')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 1 when FULL_MTR is no. Unit tests, if requested, can be ran here only!',
            name: 'WORKER_1_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 2 when FULL_MTR is no',
            name: 'WORKER_2_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 3 when FULL_MTR is no',
            name: 'WORKER_3_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 4 when FULL_MTR is no',
            name: 'WORKER_4_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 5 when FULL_MTR is no',
            name: 'WORKER_5_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 6 when FULL_MTR is no',
            name: 'WORKER_6_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 7 when FULL_MTR is no',
            name: 'WORKER_7_MTR_SUITES')
        string(
            defaultValue: '',
            description: 'Suites to be ran on worker 8 when FULL_MTR is no',
            name: 'WORKER_8_MTR_SUITES')
        booleanParam(
            defaultValue: true,
            description: 'Rerun aborted workers',
            name: 'ALLOW_ABORTED_WORKERS_RERUN')
    }
    agent {
        label 'micro-amazon'
    }
    environment {
        MAX_S3_RETRIES = 12
    }
    options {
        skipDefaultCheckout()
        skipStagesAfterUnstable()
        timeout(time: 6, unit: 'DAYS')
        buildDiscarder(logRotator(numToKeepStr: '200', artifactNumToKeepStr: '200'))
        copyArtifactPermission('percona-server-8.0-param-parallel-mtr');
    }
    stages {
        stage('Prepare') {
            steps {
                sh 'echo Prepare: \$(date -u "+%s")'
                sh '''
                    if [[ "${FULL_MTR}" == "yes" ]]; then
                        # Try to get suites split from repo. If not present, fallback to hardcoded.
                        REPLY=$(curl -Is ${RAW_VERSION_LINK}/${BRANCH}/mysql-test/suites-groups.sh | head -n 1 | awk '{print $2}')
                        if [[ ${REPLY} != 200 ]]; then
                            # Unit tests will be executed by worker 1
                            WORKER_1_MTR_SUITES="main|nobig,binlog_nogtid,innodb_undo,test_services,service_sys_var_registration,connection_control,service_status_var_registration,service_udf_registration,interactive_utilities"
                            WORKER_2_MTR_SUITES="main|big"
                            WORKER_3_MTR_SUITES="innodb"
                            WORKER_4_MTR_SUITES="auth_sec,audit_log,binlog_57_decryption,percona-pam-for-mysql,data_masking,procfs,rpl_encryption,audit_null,engines/iuds,engines/funcs,group_replication,jp,stress"
                            WORKER_5_MTR_SUITES="rpl,rpl_gtid,rpl_nogtid,binlog,sys_vars,funcs_2,opt_trace,json,collations"
                            WORKER_6_MTR_SUITES="innodb_gis,perfschema,parts,clone,query_rewrite_plugins,funcs_1"
                            WORKER_7_MTR_SUITES="rocksdb,rocksdb_stress,rocksdb_rpl,innodb_zip,information_schema,rocksdb_sys_vars"
                            WORKER_8_MTR_SUITES="component_keyring_file,innodb_fts,x,encryption,sysschema,binlog_gtid,gcol,federated,test_service_sql_api,gis,secondary_engine"
                        else
                            wget ${RAW_VERSION_LINK}/${BRANCH}/mysql-test/suites-groups.sh -O ${WORKSPACE}/suites-groups.sh

                            # Check if splitted suites contain all suites
                            wget ${RAW_VERSION_LINK}/${BRANCH}/mysql-test/mysql-test-run.pl -O ${WORKSPACE}/mysql-test-run.pl
                            chmod +x ${WORKSPACE}/suites-groups.sh
                            ${WORKSPACE}/suites-groups.sh check ${WORKSPACE}/mysql-test-run.pl

                            # Source suites split
                            source ${WORKSPACE}/suites-groups.sh
                        fi

                        echo ${WORKER_1_MTR_SUITES} > ${WORKSPACE}/worker_1.suites
                        echo ${WORKER_2_MTR_SUITES} > ${WORKSPACE}/worker_2.suites
                        echo ${WORKER_3_MTR_SUITES} > ${WORKSPACE}/worker_3.suites
                        echo ${WORKER_4_MTR_SUITES} > ${WORKSPACE}/worker_4.suites
                        echo ${WORKER_5_MTR_SUITES} > ${WORKSPACE}/worker_5.suites
                        echo ${WORKER_6_MTR_SUITES} > ${WORKSPACE}/worker_6.suites
                        echo ${WORKER_7_MTR_SUITES} > ${WORKSPACE}/worker_7.suites
                        echo ${WORKER_8_MTR_SUITES} > ${WORKSPACE}/worker_8.suites
                    fi
                '''
                script {
                    if (env.FULL_MTR == 'yes') {
                        env.WORKER_1_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_1.suites").trim()
                        env.WORKER_2_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_2.suites").trim()
                        env.WORKER_3_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_3.suites").trim()
                        env.WORKER_4_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_4.suites").trim()
                        env.WORKER_5_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_5.suites").trim()
                        env.WORKER_6_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_6.suites").trim()
                        env.WORKER_7_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_7.suites").trim()
                        env.WORKER_8_MTR_SUITES = sh(returnStdout: true, script: "cat ${WORKSPACE}/worker_8.suites").trim()
                    }
                    echo "WORKER_1_MTR_SUITES: ${env.WORKER_1_MTR_SUITES}"
                    echo "WORKER_2_MTR_SUITES: ${env.WORKER_2_MTR_SUITES}"
                    echo "WORKER_3_MTR_SUITES: ${env.WORKER_3_MTR_SUITES}"
                    echo "WORKER_4_MTR_SUITES: ${env.WORKER_4_MTR_SUITES}"
                    echo "WORKER_5_MTR_SUITES: ${env.WORKER_5_MTR_SUITES}"
                    echo "WORKER_6_MTR_SUITES: ${env.WORKER_6_MTR_SUITES}"
                    echo "WORKER_7_MTR_SUITES: ${env.WORKER_7_MTR_SUITES}"
                    echo "WORKER_8_MTR_SUITES: ${env.WORKER_8_MTR_SUITES}"

                    env.BUILD_TAG_BINARIES = "jenkins-${env.JOB_NAME}-${env.BUILD_NUMBER_BINARIES}"
                    BUILD_NUMBER_BINARIES_FOR_RERUN = env.BUILD_NUMBER_BINARIES
                    sh 'printenv'
                }
            }
        }
        stage('Build') {
            when {
                beforeAgent true
                expression { env.BUILD_NUMBER_BINARIES == '' }
            }
            agent { label LABEL }
            steps {
                timeout(time: 180, unit: 'MINUTES')  {
                    retry(3) {
                        script {
                            currentBuild.displayName = "${BUILD_NUMBER} ${CMAKE_BUILD_TYPE}/${DOCKER_OS}"
                        }
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh 'echo Prepare: \$(date -u "+%s")'
                            echo 'Checking Percona Server branch version, JEN-913 prevent wrong version run'
                            sh '''#!/bin/bash
                                MY_BRANCH_BASE_MAJOR=8
                                MY_BRANCH_BASE_MINOR=0
                                RAW_VERSION_LINK=$(echo ${GIT_REPO%.git} | sed -e "s:github.com:raw.githubusercontent.com:g")
                                REPLY=$(curl -Is ${RAW_VERSION_LINK}/${BRANCH}/MYSQL_VERSION | head -n 1 | awk '{print $2}')
                                if [[ ${REPLY} != 200 ]]; then
                                    wget ${RAW_VERSION_LINK}/${BRANCH}/VERSION -O ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                                else
                                    wget ${RAW_VERSION_LINK}/${BRANCH}/MYSQL_VERSION -O ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                                fi
                                source ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                                if [[ ${MYSQL_VERSION_MAJOR} -lt ${MY_BRANCH_BASE_MAJOR} ]] ; then
                                    echo "Are you trying to build wrong branch?"
                                    echo "You are trying to build ${MYSQL_VERSION_MAJOR}.${MYSQL_VERSION_MINOR} instead of ${MY_BRANCH_BASE_MAJOR}.${MY_BRANCH_BASE_MINOR}!"
                                    rm -f ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                                    exit 1
                                fi
                                rm -f ${WORKSPACE}/VERSION-${BUILD_NUMBER}
                            '''
                            git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                            sh '''#!/bin/bash
                                # sudo is needed for better node recovery after compilation failure
                                # if building failed on compilation stage directory will have files owned by docker user
                                sudo git reset --hard
                                sudo git clean -xdf
                                sudo rm -rf sources
                                ./local/checkout

                                echo Build: \$(date -u "+%s")
                                aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                sg docker -c "
                                    if [ \$(docker ps -q | wc -l) -ne 0 ]; then
                                        docker ps -q | xargs docker stop --time 1 || :
                                    fi
                                    ./docker/run-build ${DOCKER_OS}
                                " 2>&1 | tee build.log

                                echo Archive build: \$(date -u "+%s")
                                sed -i -e '
                                    s^/tmp/ps/^sources/^;
                                    s^/tmp/results/^sources/^;
                                    s^/xz/src/build_lzma/^/third_party/xz-4.999.9beta/^;
                                ' build.log
                                gzip build.log

                                echo MAX_S3_RETRIES: ${MAX_S3_RETRIES}

                                if [[ -f build.log.gz ]]; then
                                    retry=0
                                    until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress --acl public-read build.log.gz s3://ps-build-cache/${BUILD_TAG}/build.log.gz; do
                                        sleep 5
                                        retry=$((retry+1))
                                    done
                                fi

                                if [[ -f \$(ls sources/results/*.tar.gz | head -1) ]]; then
                                    retry=0
                                    until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress --acl public-read sources/results/*.tar.gz s3://ps-build-cache/${BUILD_TAG}/binary.tar.gz; do
                                        sleep 5
                                        retry=$((retry+1))
                                    done
                                else
                                    echo cannot find compiled archive
                                    exit 1
                                fi
                            '''
                        }
                    }
                }
            }
        }
        stage('Archive Build') {
            when {
                beforeAgent true
                expression { env.BUILD_NUMBER_BINARIES == '' }
            }
            agent { label 'micro-amazon' }
            steps {
                timeout(time: 60, unit: 'MINUTES')  {
                    retry(3) {
                        deleteDir()
                        sh '''
                            aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG}/build.log.gz ./build.log.gz
                            gunzip build.log.gz
                        '''
                        recordIssues enabledForFailure: true, tools: [gcc(pattern: 'build.log')]
                    }
                }
                script {
                    env.BUILD_TAG_BINARIES = env.BUILD_TAG
                    BUILD_NUMBER_BINARIES_FOR_RERUN = env.BUILD_NUMBER
                }
            }
        }
        stage('Test') {
            parallel {
                stage('Test - 1') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_1_MTR_SUITES?.trim()) }
                    }
                    agent { label WORKER_1_LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_1_ABORTED = true
                                echo "WORKER_1_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            if [[ \$CI_FS_MTR == 'yes' ]]; then
                                                if [[ ! -f /mnt/ci_disk_\$CMAKE_BUILD_TYPE.img ]] && [[ -z \$(mount | grep /mnt/ci_disk_dir_\$CMAKE_BUILD_TYPE) ]]; then
                                                    sudo dd if=/dev/zero of=/mnt/ci_disk_\$CMAKE_BUILD_TYPE.img bs=1G count=20
                                                    sudo /sbin/mkfs.vfat /mnt/ci_disk_\$CMAKE_BUILD_TYPE.img
                                                    sudo mkdir -p /mnt/ci_disk_dir_\$CMAKE_BUILD_TYPE
                                                    sudo mount -o loop -o uid=27 -o gid=27 -o check=r /mnt/ci_disk_\$CMAKE_BUILD_TYPE.img /mnt/ci_disk_dir_\$CMAKE_BUILD_TYPE
                                                fi
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            # Allow unit tests execution only on 1st worker if requested
                                            # Allow case insensitive FS tests only on 1st worker if requested
                                            # Allow CI FS tests only on 1st worker if requested
                                            # Allow keyring_vault tests only on 1st worker if requested
                                            # Allow ZenFS tests only on 1st worker if requested

                                            export MTR_SUITES=${WORKER_1_MTR_SUITES}

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 1
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_1_ABORTED = false
                                echo "WORKER_1_ABORTED = false"
                            }
                        }
                    }
                } // 1
                stage('Test - 2') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_2_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_2_ABORTED = true
                                echo "WORKER_2_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            export MTR_SUITES=${WORKER_2_MTR_SUITES}
                                            MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                            CI_FS_MTR=no
                                            KEYRING_VAULT_MTR=no
                                            ZEN_FS_MTR=no

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 2
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_2_ABORTED = false
                                echo "WORKER_2_ABORTED = false"
                            }
                        }
                    }
                } // 2
                stage('Test - 3') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_3_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_3_ABORTED = true
                                echo "WORKER_3_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            export MTR_SUITES=${WORKER_3_MTR_SUITES}
                                            MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                            CI_FS_MTR=no
                                            KEYRING_VAULT_MTR=no
                                            ZEN_FS_MTR=no

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 3
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_3_ABORTED = false
                                echo "WORKER_3_ABORTED = false"
                            }
                        }
                    }
                } // 3
                stage('Test - 4') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_4_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_4_ABORTED = true
                                echo "WORKER_4_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            export MTR_SUITES=${WORKER_4_MTR_SUITES}
                                            MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                            CI_FS_MTR=no
                                            KEYRING_VAULT_MTR=no
                                            ZEN_FS_MTR=no

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 4
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_4_ABORTED = false
                                echo "WORKER_4_ABORTED = false"
                            }
                        }
                    }
                } // 4
                stage('Test - 5') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_5_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_5_ABORTED = true
                                echo "WORKER_5_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            export MTR_SUITES=${WORKER_5_MTR_SUITES}
                                            MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                            CI_FS_MTR=no
                                            KEYRING_VAULT_MTR=no
                                            ZEN_FS_MTR=no

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 5
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_5_ABORTED = false
                                echo "WORKER_5_ABORTED = false"
                            }
                        }
                    }
                } // 5
                stage('Test - 6') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_6_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_6_ABORTED = true
                                echo "WORKER_6_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            export MTR_SUITES=${WORKER_6_MTR_SUITES}
                                            MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                            CI_FS_MTR=no
                                            KEYRING_VAULT_MTR=no
                                            ZEN_FS_MTR=no

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 6
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_6_ABORTED = false
                                echo "WORKER_6_ABORTED = false"
                            }
                        }
                    }
                } // 6
                stage('Test - 7') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_7_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_7_ABORTED = true
                                echo "WORKER_7_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            export MTR_SUITES=${WORKER_7_MTR_SUITES}
                                            MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                            CI_FS_MTR=no
                                            KEYRING_VAULT_MTR=no
                                            ZEN_FS_MTR=no

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 7
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_7_ABORTED = false
                                echo "WORKER_7_ABORTED = false"
                            }
                        }
                    }
                } // 7
                stage('Test - 8') {
                    when {
                        beforeAgent true
                        expression { (env.WORKER_8_MTR_SUITES?.trim()) }
                    }
                    agent { label LABEL }
                    steps {
                        catchError(buildResult: 'UNSTABLE') {
                            script {
                                WORKER_8_ABORTED = true
                                echo "WORKER_8_ABORTED = true"
                            }
                            timeout(time: pipeline_timeout, unit: 'HOURS')  {
                                git branch: JENKINS_SCRIPTS_BRANCH, url: JENKINS_SCRIPTS_REPO
                                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'c8b933cd-b8ca-41d5-b639-33fe763d3f68', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                                    withCredentials([
                                        string(credentialsId: 'VAULT_V1_DEV_ROOT_TOKEN', variable: 'VAULT_V1_DEV_ROOT_TOKEN'),
                                        string(credentialsId: 'VAULT_V2_DEV_ROOT_TOKEN', variable: 'VAULT_V2_DEV_ROOT_TOKEN')]) {
                                        sh '''#!/bin/bash
                                            sudo git reset --hard
                                            sudo git clean -xdf
                                            rm -rf sources/results
                                            sudo git -C sources reset --hard || :
                                            sudo git -C sources clean -xdf   || :

                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 cp --no-progress s3://ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz ./sources/results/binary.tar.gz; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done

                                            if [ -f /usr/bin/yum ]; then
                                                sudo yum -y install jq gflags-devel
                                            else
                                                sudo apt-get install -y jq libgflags-dev libjemalloc-dev
                                            fi

                                            echo Test: \$(date -u "+%s")

                                            export MTR_SUITES=${WORKER_8_MTR_SUITES}
                                            MTR_ARGS=${MTR_ARGS//"--unit-tests-report"/""}
                                            CI_FS_MTR=no
                                            KEYRING_VAULT_MTR=no
                                            ZEN_FS_MTR=no

                                            aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                            sg docker -c "
                                                if [ \$(docker ps -a -q | wc -l) -ne 0 ]; then
                                                    docker ps -a -q | xargs docker stop --time 1 || :
                                                    docker rm --force consul vault-prod-v{1..2} vault-dev-v{1..2} || :
                                                fi
                                                ulimit -a
                                                ./docker/run-test-parallel-mtr ${DOCKER_OS} 8
                                            "

                                            echo Archive test: \$(date -u "+%s")
                                            retry=0
                                            until [ $retry -eq ${MAX_S3_RETRIES} ] || aws s3 sync --no-progress --acl public-read --exclude 'binary.tar.gz' ./sources/results/ s3://ps-build-cache/${BUILD_TAG_BINARIES}/; do
                                                sleep 5
                                                retry=$((retry+1))
                                            done
                                        '''
                                    }
                                }
                            }
                            script {
                                WORKER_8_ABORTED = false
                                echo "WORKER_8_ABORTED = false"
                            }
                        }
                    }
                } // 8
            } //parallel
        }
        stage('Archive') {
            agent { label 'micro-amazon' }
            steps {
                retry(3) {
                deleteDir()
                sh '''#!/bin/bash
                    aws s3 sync --no-progress --exclude 'binary.tar.gz' s3://ps-build-cache/${BUILD_TAG_BINARIES}/ ./

                    echo "
                        binary    - https://s3.us-east-2.amazonaws.com/ps-build-cache/${BUILD_TAG_BINARIES}/binary.tar.gz
                        build log - https://s3.us-east-2.amazonaws.com/ps-build-cache/${BUILD_TAG_BINARIES}/build.log.gz
                    " > public_url
                '''
                step([$class: 'JUnitResultArchiver', testResults: '*.xml', healthScaleFactor: 1.0])
                archiveArtifacts 'build.log.gz,*.xml,public_url'
                }
            }
        }
    }
    post {
        always {
            script {
                if (env.ALLOW_ABORTED_WORKERS_RERUN == 'true') {
                    echo "allow aborted reruns ${env.ALLOW_ABORTED_WORKERS_RERUN}"
                    echo "WORKER_1_ABORTED: $WORKER_1_ABORTED"
                    def rerunNeeded = false
                    def WORKER_1_RERUN_SUITES = ""
                    def WORKER_2_RERUN_SUITES = ""
                    def WORKER_3_RERUN_SUITES = ""
                    def WORKER_4_RERUN_SUITES = ""
                    def WORKER_5_RERUN_SUITES = ""
                    def WORKER_6_RERUN_SUITES = ""
                    def WORKER_7_RERUN_SUITES = ""
                    def WORKER_8_RERUN_SUITES = ""

                    if (WORKER_1_ABORTED) {
                        echo "rerun worker 1"
                        WORKER_1_RERUN_SUITES = env.WORKER_1_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_2_ABORTED) {
                        echo "rerun worker 2"
                        WORKER_2_RERUN_SUITES = env.WORKER_2_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_3_ABORTED) {
                        echo "rerun worker 3"
                        WORKER_3_RERUN_SUITES = env.WORKER_3_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_4_ABORTED) {
                        echo "rerun worker 4"
                        WORKER_4_RERUN_SUITES = env.WORKER_4_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_5_ABORTED) {
                        echo "rerun worker 5"
                        WORKER_5_RERUN_SUITES = env.WORKER_5_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_6_ABORTED) {
                        echo "rerun worker 6"
                        WORKER_6_RERUN_SUITES = env.WORKER_6_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_7_ABORTED) {
                        echo "rerun worker 7"
                        WORKER_7_RERUN_SUITES = env.WORKER_7_MTR_SUITES
                        rerunNeeded = true
                    }
                    if (WORKER_8_ABORTED) {
                        echo "rerun worker 8"
                        WORKER_8_RERUN_SUITES = env.WORKER_8_MTR_SUITES
                        rerunNeeded = true
                    }
                    echo "rerun needed: $rerunNeeded"
                    if (rerunNeeded) {
                        echo "restarting aborted workers"
                        build job: 'percona-server-8.0-pipeline-parallel-mtr',
                        wait: false,
                        parameters: [
                            string(name:'BUILD_NUMBER_BINARIES', value: BUILD_NUMBER_BINARIES_FOR_RERUN),
                            string(name:'GIT_REPO', value: env.GIT_REPO),
                            string(name:'BRANCH', value: env.BRANCH),
                            string(name:'DOCKER_OS', value: env.DOCKER_OS),
                            string(name:'JOB_CMAKE', value: env.JOB_CMAKE),
                            string(name:'COMPILER', value: env.COMPILER),
                            string(name:'CMAKE_BUILD_TYPE', value: env.CMAKE_BUILD_TYPE),
                            string(name:'ANALYZER_OPTS', value: env.ANALYZER_OPTS),
                            string(name:'WITH_ROCKSDB', value: env.WITH_ROCKSDB),
                            string(name:'WITH_ROUTER', value: env.WITH_ROUTER),
                            string(name:'WITH_MYSQLX', value: env.WITH_MYSQLX),
                            string(name:'WITH_KEYRING_VAULT', value: env.WITH_KEYRING_VAULT),
                            string(name:'CMAKE_OPTS', value: env.CMAKE_OPTS),
                            string(name:'MAKE_OPTS', value: env.MAKE_OPTS),
                            string(name:'DEFAULT_TESTING', value: env.DEFAULT_TESTING),
                            string(name:'ZEN_FS_MTR', value: env.ZEN_FS_MTR),
                            string(name:'CI_FS_MTR', value: env.CI_FS_MTR),
                            string(name:'MTR_ARGS', value: env.MTR_ARGS),
                            string(name:'MTR_REPEAT', value: env.MTR_REPEAT),
                            string(name:'KEYRING_VAULT_MTR', value: env.KEYRING_VAULT_MTR),
                            string(name:'KEYRING_VAULT_V1_VERSION', value: env.KEYRING_VAULT_V1_VERSION),
                            string(name:'KEYRING_VAULT_V2_VERSION', value: env.KEYRING_VAULT_V2_VERSION),
                            string(name:'LABEL', value: env.LABEL),
                            string(name:'FULL_MTR', value:'no'),
                            string(name:'WORKER_1_MTR_SUITES', value: WORKER_1_RERUN_SUITES),
                            string(name:'WORKER_2_MTR_SUITES', value: WORKER_2_RERUN_SUITES),
                            string(name:'WORKER_3_MTR_SUITES', value: WORKER_3_RERUN_SUITES),
                            string(name:'WORKER_4_MTR_SUITES', value: WORKER_4_RERUN_SUITES),
                            string(name:'WORKER_5_MTR_SUITES', value: WORKER_5_RERUN_SUITES),
                            string(name:'WORKER_6_MTR_SUITES', value: WORKER_6_RERUN_SUITES),
                            string(name:'WORKER_7_MTR_SUITES', value: WORKER_7_RERUN_SUITES),
                            string(name:'WORKER_8_MTR_SUITES', value: WORKER_8_RERUN_SUITES),
                            booleanParam(name: 'ALLOW_ABORTED_WORKERS_RERUN', value: false),
                            string(name:'BUILD_DISPLAY_NAME', value: "${BUILD_NUMBER} retry")
                        ]
                    }
                }  // env.ALLOW_ABORTED_WORKERS_RERUN
            }
            sh '''
                echo Finish: \$(date -u "+%s")
            '''
        }
    }
}
